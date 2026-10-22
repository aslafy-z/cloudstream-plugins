package fr.novo19

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
internal data class NovoItem(val id: String, val url: String, val title: String, val series: Boolean, val poster: String?, val description: String?, val season: Int?, val episode: Int?)
internal data class NovoDetails(val item: NovoItem, val episodes: List<NovoItem>)
internal data class NovoStream(val url: String, val license: String?)
internal class NovoClient {
    companion object {
        const val SITE = "https://novo19.ouest-france.fr"
        private const val API = "https://exposure.api.redbee.live/v2/customer/OuestFrance/businessunit/novoplus"
        private val ID = Regex("[A-Za-z0-9_-]+")
        fun address(path: String): String {
            val url = SITE.toHttpUrl().resolve(path) ?: throw IOException("Invalid NOVO19 catalogue address.")
            require(url.scheme == "https" && url.host == "novo19.ouest-france.fr" && url.port == 443) { "Invalid NOVO19 catalogue address." }
            return url.toString()
        }
        fun item(json: JSONObject): NovoItem? {
            if (json.text("type") !in setOf("SERIE", "VOD", "EPISODE")) return null
            val id = json.text("id")?.takeIf(ID::matches) ?: return null
            val numbers = json.text("subtitle").orEmpty()
            return NovoItem(id, address(json.text("href") ?: return null), json.text("title") ?: return null, json.text("type") == "SERIE",
                json.optJSONObject("thumbnail")?.text("src"), json.text("description"),
                Regex("S(\\d+)", RegexOption.IGNORE_CASE).find(numbers)?.groupValues?.get(1)?.toIntOrNull(),
                Regex("E(\\d+)", RegexOption.IGNORE_CASE).find(numbers)?.groupValues?.get(1)?.toIntOrNull())
        }
        fun queries(data: JSONObject, section: String? = null): List<JSONObject> {
            val queries = data.optJSONObject("initialState")?.optJSONObject("api")?.optJSONObject("queries") ?: return emptyList()
            return queries.keys().asSequence().filter { it.startsWith("bffTile") && (section == null || it.contains("/sections/$section/")) }
                .mapNotNull { queries.optJSONObject(it)?.optJSONObject("data") }.toList()
        }
    }
    private val http = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()
    private val authHttp = http.newBuilder().followRedirects(false).followSslRedirects(false).build()
    private val cache = LinkedHashMap<String, Pair<Long, String>>()
    private val device = UUID.randomUUID().toString()
    private fun execute(request: Request, authenticated: Boolean = false): String = (if (authenticated) authHttp else http)
        .newCall(request.newBuilder().header("User-Agent", "Mozilla/5.0").build()).execute().use {
            if (!it.isSuccessful) throw IOException("NOVO19 request failed (HTTP ${it.code}). Check regional availability.")
            it.body?.string() ?: throw IOException("NOVO19 returned an empty response.")
        }
    private fun page(path: String): JSONObject {
        val url = address(path)
        synchronized(cache) { cache[url]?.takeIf { System.currentTimeMillis() - it.first < 600_000 }?.let { return JSONObject(it.second) } }
        val html = execute(Request.Builder().url(url).build())
        val embedded = Regex("<script[^>]*id=\"__NEXT_DATA__\"[^>]*>(.*?)</script>", RegexOption.DOT_MATCHES_ALL).find(html)?.groupValues?.get(1)
            ?: throw IOException("NOVO19 catalogue data is unavailable. The plugin may need an update.")
        val data = JSONObject(embedded).getJSONObject("props").getJSONObject("pageProps")
        synchronized(cache) { if (cache.size >= 80) cache.remove(cache.keys.first()); cache[url] = System.currentTimeMillis() to data.toString() }
        return data
    }
    private fun categories() = page("/categories").getJSONObject("serverData").getJSONObject("page").optJSONArray("rails").objects()
        .filter { it.text("type") == "CAROUSEL" && it.text("title") != null && it.text("title") != "Nos podcasts" }
    private fun tiles(path: String, section: String? = null, visited: MutableSet<String> = mutableSetOf()): List<NovoItem> {
        val url = address(path)
        check(visited.size < 50 && visited.add(url)) { "NOVO19 catalogue pagination did not finish." }
        return queries(page(url), section).flatMap { data ->
            val items = data.optJSONArray("tiles").objects().mapNotNull(::item)
            val more = data.optJSONObject("more")?.text("href")
            items + if (more != null && address(more) != url) tiles(more, null, visited) else emptyList()
        }.distinctBy { it.id }
    }
    fun home(number: Int) = categories().drop((number - 1).coerceAtLeast(0) * 3).take(3).mapNotNull { category ->
        val id = category.text("id") ?: return@mapNotNull null
        val items = tiles("/voir-plus/rail/categories/$id").filter { "/details/" in it.url }
        if (items.isEmpty()) null else category.getString("title") to items
    }
    fun search(query: String): List<NovoItem> = categories().flatMap { category ->
        tiles("/voir-plus/rail/categories/${category.getString("id")}")
    }.filter { it.title.contains(query, ignoreCase = true) && "/details/" in it.url }.distinctBy { it.id }
    fun details(url: String): NovoDetails {
        val data = page(url)
        val page = data.getJSONObject("serverData").getJSONObject("page")
        val content = page.optJSONObject("content") ?: throw IOException("NOVO19 programme details are unavailable.")
        val rails = page.optJSONArray("rails").objects().filter { it.text("id") != "reco" && it.text("type") == "CAROUSEL" }
        val episodes = rails.flatMap { rail -> tiles(url, rail.getString("id")) }.filter { !it.series }
            .distinctBy { it.id }.sortedWith(compareBy({ it.season ?: 1 }, { it.episode ?: Int.MAX_VALUE }))
        val id = content.text("id")?.takeIf(ID::matches) ?: throw IOException("NOVO19 returned no programme identifier.")
        val meta = NovoItem(id, address(url), content.text("title") ?: page.text("title") ?: "NOVO19", rails.isNotEmpty(),
            content.optJSONObject("background")?.text("src") ?: page.optJSONObject("metadata")?.optJSONObject("image")?.text("src"),
            content.text("description"), null, null)
        if (meta.series && episodes.isEmpty()) throw IOException("No available replays in this NOVO19 series.")
        return NovoDetails(meta, episodes)
    }
    fun playback(id: String): NovoStream {
        require(ID.matches(id)) { "Invalid NOVO19 replay identifier." }
        val body = JSONObject().put("deviceId", device).put("device", JSONObject().put("name", "Browser").put("type", "WEB"))
        val token = JSONObject(execute(Request.Builder().url("$API/auth/anonymous")
            .post(body.toString().toRequestBody("application/json".toMediaType())).build(), true)).text("sessionToken")
            ?: throw IOException("NOVO19 anonymous authentication failed.")
        val json = JSONObject(execute(Request.Builder().url("$API/entitlement/$id/play").header("Authorization", "Bearer $token").build(), true))
        val formats = json.optJSONArray("formats").objects()
        val format = formats.firstOrNull { it.text("format") == "DASH" && it.optJSONObject("drm")?.has("com.widevine.alpha") == true }
            ?: formats.firstOrNull { it.text("format") == "DASH" && (it.optJSONObject("drm")?.length() ?: 0) == 0 }
            ?: throw IOException("No compatible NOVO19 DASH stream is available.")
        val manifest = format.text("mediaLocator") ?: throw IOException("NOVO19 returned no manifest.")
        val drm = format.optJSONObject("drm")?.optJSONObject("com.widevine.alpha")
        val license = if (drm != null) drm.text("licenseServerUrl") ?: throw IOException("NOVO19 returned no license endpoint.") else null
        require(manifest.toHttpUrl().scheme == "https" && (license == null || license.toHttpUrl().scheme == "https")) { "Invalid NOVO19 playback address." }
        return NovoStream(manifest, license)
    }
}
