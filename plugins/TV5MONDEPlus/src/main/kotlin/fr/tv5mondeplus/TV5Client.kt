package fr.tv5mondeplus

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONArray
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

internal fun JSONObject.text(key: String): String? = optString(key).takeIf { it.isNotBlank() && it != "null" }
internal fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull(::optJSONObject)
internal data class TV5Item(val id: String, val title: String, val series: Boolean, val poster: String?, val description: String?, val season: Int? = null, val episode: Int? = null)
internal data class TV5Details(val item: TV5Item, val episodes: List<TV5Item>)
internal data class TV5Stream(val url: String, val license: String?)
internal class TV5Client {
    companion object {
        const val API = "https://api.tv5mondeplus.com"
        const val SITE = "https://www.tv5mondeplus.com"
        private const val TENANT = "/customer/TV5MONDE/businessunit/TV5MONDEplus"
        private const val CONFIG = "/v2/whitelabel$TENANT/config/sandwich"
        private val ID = Regex("[A-Za-z0-9_-]+")
        private val hashes = mapOf(
            "VODContentDetails" to "d34748a7f69fd499dffa6845440c443194aed3b05de23bc5568a34de4689a80d",
            "VODContentSeasons" to "3dd0d1d4b917a2f453410c92603fe7d066ab3c5eebf050549fcec74006cfd367",
            "VODContentEpisodes" to "fe8d9bf478e3542a91dd70d989747fba7097b169d7de9b1c8a18748281f29a13")
        // This gateway returns an empty graph when the contentId colon lacks a following space.
        fun variables(id: String) = "{\"contentId\": ${JSONObject.quote(id)}}"
        fun asset(json: JSONObject): TV5Item? {
            if (json.optBoolean("live") || json.text("type") !in setOf("MOVIE", "TV_SHOW", "EPISODE")) return null
            val localized = json.optJSONArray("localized").objects()
            val fr = localized.firstOrNull { it.text("locale") == "fr" } ?: localized.firstOrNull() ?: return null
            val images = fr.optJSONArray("images").objects()
            return TV5Item(json.text("assetId")?.takeIf(ID::matches) ?: return null, fr.text("title") ?: return null,
                json.text("type") == "TV_SHOW", (images.firstOrNull { it.text("type") == "poster" } ?: images.firstOrNull())?.text("url"),
                fr.text("description"))
        }
        fun content(json: JSONObject, season: Int? = null): TV5Item? {
            val id = json.optJSONArray("externalIds").objects().firstOrNull { it.text("scheme") == "DATA_PROVIDER" }?.text("identifier") ?: return null
            if (!ID.matches(id)) return null
            return TV5Item(id, json.text("title") ?: return null, json.text("__typename") == "Series",
                json.optJSONArray("artworks").objects().firstNotNullOfOrNull { it.text("source") }, json.text("description"), season,
                json.optInt("episodeNumber").takeIf { it > 0 })
        }
        fun selectStream(json: JSONObject): TV5Stream {
            val streams = json.optJSONArray("formats").objects()
            val format = streams.firstOrNull { it.text("format") == "DASH" && it.optJSONObject("drm")?.has("com.widevine.alpha") == true }
                ?: streams.firstOrNull { it.text("format") == "DASH" && (it.optJSONObject("drm")?.length() ?: 0) == 0 }
                ?: throw IOException("No compatible TV5MONDEplus DASH stream is available.")
            val url = format.text("mediaLocator") ?: throw IOException("TV5MONDEplus returned no manifest.")
            val drm = format.optJSONObject("drm")?.optJSONObject("com.widevine.alpha")
            val license = if (drm != null) drm.text("licenseServerUrl") ?: throw IOException("TV5MONDEplus returned no Widevine license endpoint.") else null
            require(url.toHttpUrl().scheme == "https" && (license == null || license.toHttpUrl().scheme == "https")) { "Invalid TV5MONDEplus playback address." }
            return TV5Stream(url, license)
        }
    }
    private val http = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).callTimeout(30, TimeUnit.SECONDS).build()
    private val cache = LinkedHashMap<String, Pair<Long, String>>()
    private val device = UUID.randomUUID().toString()
    private fun execute(request: Request): JSONObject = http.newCall(request.newBuilder().header("User-Agent", "Mozilla/5.0")
        .header("Accept-Language", "fr").build()).execute().use {
        if (!it.isSuccessful) throw IOException("TV5MONDEplus request failed (HTTP ${it.code}). Check regional availability.")
        JSONObject(it.body?.string() ?: throw IOException("TV5MONDEplus returned an empty response."))
    }
    private fun get(path: String): JSONObject {
        val url = (if (path.startsWith('/')) API + path else path).toHttpUrl()
        require(url.scheme == "https" && url.port == 443 && url.host in setOf("api.tv5mondeplus.com", "www.tv5mondeplus.com")) { "Invalid TV5MONDEplus catalogue address." }
        val key = url.toString()
        synchronized(cache) { cache[key]?.takeIf { System.currentTimeMillis() - it.first < 600_000 }?.let { return JSONObject(it.second) } }
        val value = execute(Request.Builder().url(url).build())
        synchronized(cache) { if (cache.size >= 80) cache.remove(cache.keys.first()); cache[key] = System.currentTimeMillis() to value.toString() }
        return value
    }
    fun home(page: Int): List<Pair<String, List<TV5Item>>> {
        val config = get("$CONFIG?onlyPublished=true")
        val home = config.optJSONObject("components")?.optJSONArray("homePage").objects().firstOrNull()?.text("referenceUrl") ?: throw IOException("TV5MONDEplus home page is unavailable.")
        val shelves = get(home).optJSONObject("components")?.optJSONArray("pageBody").objects()
            .filter { it.text("appSubType") in setOf("Curated", "TagsQuery") }.drop((page - 1).coerceAtLeast(0) * 5).take(5)
        return shelves.mapNotNull { shelf ->
            val component = shelf.text("referenceUrl")?.let(::get) ?: return@mapNotNull null
            val url = component.optJSONObject("contentUrl")?.text("url") ?: return@mapNotNull null
            val items = get(url).optJSONArray("items").objects().mapNotNull { asset(it.optJSONObject("asset") ?: it) }
            if (items.isEmpty()) null else (component.text("name") ?: "TV5MONDEplus") to items
        }
    }
    fun search(query: String): List<TV5Item> {
        val url = (API + "/v2$TENANT/content/search/query/").toHttpUrl().newBuilder().addPathSegment(query)
            .addQueryParameter("locale", "fr").addQueryParameter("client", "json").addQueryParameter("onlyPublished", "true").build()
        return get(url.toString()).optJSONArray("items").objects().mapNotNull { asset(it.optJSONObject("asset") ?: it) }.distinctBy { it.id }
    }
    private fun graph(operation: String, id: String): JSONObject {
        val url = "$SITE/api/graphql/v1/".toHttpUrl().newBuilder().addQueryParameter("operationName", operation)
            .addQueryParameter("variables", variables(id))
            .addQueryParameter("extensions", JSONObject().put("persistedQuery", JSONObject().put("version", 1).put("sha256Hash", hashes.getValue(operation))).toString()).build()
        val json = get(url.toString())
        if (json.optJSONArray("errors")?.length()?.let { it > 0 } == true) throw IOException("TV5MONDEplus catalogue query is unavailable. The plugin may need an update.")
        return json.optJSONObject("data")?.optJSONObject("lookupContent") ?: throw IOException("This TV5MONDEplus programme is unavailable.")
    }
    fun details(id: String): TV5Details {
        require(ID.matches(id)) { "Invalid TV5MONDEplus programme identifier." }
        val json = graph("VODContentDetails", "redbee:$id:fr")
        val item = content(json) ?: throw IOException("TV5MONDEplus programme details are unavailable.")
        if (!item.series) return TV5Details(item, emptyList())
        val seasons = graph("VODContentSeasons", json.getString("id")).optJSONObject("seasons")?.optJSONArray("items").objects()
        val episodes = seasons.flatMap { season ->
            val number = season.optInt("seasonNumber").takeIf { it > 0 }
            val items = graph("VODContentEpisodes", season.getString("id")).optJSONObject("episodes")?.optJSONArray("items").objects().mapNotNull { content(it, number) }
            val total = season.optJSONObject("episodes")?.optJSONObject("pageInfo")?.optInt("total", items.size) ?: items.size
            if (items.size < total) throw IOException("TV5MONDEplus returned an incomplete season. The plugin needs a catalogue update.")
            items
        }.distinctBy { it.id }.sortedWith(compareBy({ it.season ?: 1 }, { it.episode ?: Int.MAX_VALUE }))
        if (episodes.isEmpty()) throw IOException("No available episodes in this TV5MONDEplus programme.")
        return TV5Details(item, episodes)
    }
    fun playback(id: String): TV5Stream {
        require(ID.matches(id)) { "Invalid TV5MONDEplus replay identifier." }
        // Fresh anonymous authorization avoids persisting playback tokens or guessing their expiry.
        val body = JSONObject().put("deviceId", device).put("device", JSONObject().put("deviceId", device)
            .put("type", "WEB").put("name", "Chrome").put("width", 1920).put("height", 1080))
        val token = execute(Request.Builder().url(API + "/v1$TENANT/auth/anonymous")
            .post(body.toString().toRequestBody("application/json".toMediaType())).build()).text("sessionToken")
            ?: throw IOException("TV5MONDEplus anonymous authentication failed.")
        return selectStream(execute(Request.Builder().url(API + "/v2$TENANT/entitlement/$id/play")
            .header("Authorization", "Bearer $token").build()))
    }
}
