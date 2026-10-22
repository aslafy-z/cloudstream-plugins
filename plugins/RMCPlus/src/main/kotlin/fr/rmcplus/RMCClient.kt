package fr.rmcplus

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONArray
import java.io.IOException
import java.util.concurrent.TimeUnit

internal class RMCException(val status: Int, message: String) : IOException(message)
internal fun JSONObject.text(key: String): String? = optString(key).takeIf { it.isNotBlank() && it != "null" }
internal fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull(::optJSONObject)
internal data class RMCItem(val endpoint: String, val title: String, val poster: String?, val description: String?, val season: Int?, val episode: Int?)
internal data class RMCDetails(val item: RMCItem, val episodes: List<RMCItem>, val recommendations: List<RMCItem> = emptyList())
internal data class RMCStream(val url: String, val dash: Boolean, val token: String?)

internal class RMCClient {
    companion object {
        const val ROOT = "https://bff.rmcplus.fr/v1"
        const val SITE = "https://www.rmcplus.fr"
        const val LICENSE = "https://lic.drmtoday.com/license-proxy-widevine/cenc/"
        const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/130.0.0.0 Safari/537.36"
        fun endpoint(path: String): String {
            val url = (if (path.startsWith("/")) ROOT + path else path).toHttpUrl()
            require(url.scheme == "https" && url.host == "bff.rmcplus.fr" && url.port == 443 && url.encodedPath.startsWith("/v1/")) { "Invalid RMC+ endpoint." }
            return url.newBuilder().setQueryParameter("model", "androidtv-ott").build().toString()
        }
        fun item(json: JSONObject): RMCItem? {
            val path = json.optJSONArray("call_to_actions").objects().mapNotNull { it.text("endpoint") }
                .firstOrNull { it.startsWith("/page?") } ?: return null
            val image = json.optJSONObject("background_image")
            val title = json.text("titre") ?: image?.text("alt") ?: return null
            val numbers = json.optJSONObject("etiquette")?.text("saison_episode").orEmpty()
            return RMCItem(endpoint(path), title, image?.text("url"), json.text("description") ?: json.optJSONObject("immersif")?.text("description"),
                Regex("S(\\d+)", RegexOption.IGNORE_CASE).find(numbers)?.groupValues?.get(1)?.toIntOrNull(),
                Regex("E(\\d+)", RegexOption.IGNORE_CASE).find(numbers)?.groupValues?.get(1)?.toIntOrNull())
        }
        fun episodes(sections: List<JSONObject>): List<RMCItem> = sections.filter { it.text("type_tuile") == "episode" || it.text("type") == "mosaique" }
            .flatMap { it.optJSONArray("items").objects().mapNotNull(::item) }
            .filter { it.endpoint.toHttpUrl().queryParameter("page_type") == "player" }
            .distinctBy { it.endpoint }.sortedWith(compareBy({ it.season ?: 1 }, { it.episode ?: Int.MAX_VALUE }))
    }
    private val http = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).callTimeout(30, TimeUnit.SECONDS).build()
    private val cache = LinkedHashMap<String, Pair<Long, String>>()
    private var clientId: String? = null
    private fun execute(request: Request): String = http.newCall(request.newBuilder().header("User-Agent", UA).build()).execute().use {
        if (!it.isSuccessful) throw RMCException(it.code, when (it.code) {
            401 -> "RMC+ session expired. Sign in again in plugin settings."
            403 -> "RMC+ denied access. Check your account and regional availability."
            404 -> "This RMC+ replay is no longer available."
            else -> "RMC+ request failed (HTTP ${it.code})."
        })
        it.body?.string() ?: throw IOException("Empty RMC+ response.")
    }
    fun page(path: String, token: String? = null): JSONObject {
        val url = endpoint(path)
        if (token == null) synchronized(cache) { cache[url]?.takeIf { System.currentTimeMillis() - it.first < 600_000 }?.let { return JSONObject(it.second) } }
        val request = Request.Builder().url(url).apply { if (token != null) header("Authorization", "Bearer $token") }.build()
        val raw = execute(request)
        if (token == null) synchronized(cache) { if (cache.size >= 80) cache.remove(cache.keys.first()); cache[url] = System.currentTimeMillis() to raw }
        return JSONObject(raw)
    }
    @Synchronized fun login(email: String, password: String): String {
        if (clientId == null) {
            val html = execute(Request.Builder().url(SITE).build())
            val scripts = Regex("src=\"([^\"]+\\.js[^\"]*)\"").findAll(html).map { it.groupValues[1] }.distinct().take(80)
            for (path in scripts) {
                val url = SITE.toHttpUrl().resolve(path) ?: continue
                if (url.host != "www.rmcplus.fr" || url.scheme != "https") continue
                val script = execute(Request.Builder().url(url).build())
                clientId = Regex("\"getEffectiveAuthenticationClientId\",0,\\(\\)=>\"([^\"]+)").find(script)?.groupValues?.get(1)
                if (clientId != null) break
            }
        }
        val id = clientId ?: throw IOException("RMC+ login configuration is unavailable.")
        val body = JSONObject().put("email", email).put("password", password).put("clientId", id).toString()
        val response = JSONObject(execute(Request.Builder().url("https://connect.rmcbfm.com/api/sign-in?model=androidtv-ott")
            .post(body.toRequestBody("application/json".toMediaType())).build()))
        return response.optJSONObject("AuthenticationResult")?.text("IdToken") ?: throw RMCException(401, "RMC+ sign-in failed. Check your credentials.")
    }
    fun home(id: String, number: Int) = RMCDiagnostics.trace("Load catalogue") {
        page("/page?page_type=default&page_id=$id").optJSONArray("sections").objects()
            .filter { it.text("type_tuile") !in setOf("direct", "reprise-de-lecture") }
            .drop((number - 1).coerceAtLeast(0) * 6).take(6)
            .mapNotNull { original ->
                val section = hydrate(original)
                val items = section.optJSONArray("items").objects().mapNotNull(::item)
                if (items.isEmpty()) null else (section.text("titre") ?: "Featured") to items
            }
    }
    private fun hydrate(section: JSONObject): JSONObject {
        val count = section.optJSONArray("items")?.length() ?: 0
        val endpoint = section.text("endpoint")
        return if (endpoint != null && endpoint.startsWith("/composant/") &&
            (count == 0 || section.optInt("total_items", count) > count)) page(endpoint) else section
    }
    fun search(query: String) = RMCDiagnostics.trace("Search") {
        val url = "$ROOT/recherche".toHttpUrl().newBuilder().addQueryParameter("page_id", "recherche")
            .addQueryParameter("page_type", "recherche").addQueryParameter("query", query).build()
        page(url.toString()).optJSONArray("sections").objects().flatMap { it.optJSONArray("items").objects().mapNotNull(::item) }.distinctBy { it.endpoint }
    }
    fun details(path: String): RMCDetails = RMCDiagnostics.trace("Load programme") {
        val root = page(path)
        val sections = root.optJSONArray("sections").objects().map {
            if (it.text("type_tuile") == "episode" || it.text("type") == "mosaique") hydrate(it) else it
        }
        val hero = sections.firstOrNull { it.text("type") == "herobannerFIP" }?.optJSONObject("item")
        val episodeList = episodes(sections)
        if (hero == null) {
            val children = sections.flatMap { hydrate(it).optJSONArray("items").objects().mapNotNull(::item) }.distinctBy { it.endpoint }
            if (children.isEmpty()) throw IOException("RMC+ returned no available programme.")
            return@trace RMCDetails(RMCItem(endpoint(path), root.text("titre") ?: "RMC+", children.first().poster, null, null, null), emptyList(), children)
        }
        val meta = item(hero) ?: throw IOException("This RMC+ programme has no available replay.")
        RMCDetails(meta, episodeList)
    }
    fun playback(path: String, auth: RMCAuth): RMCStream = RMCDiagnostics.trace("Resolve replay") {
        val result = try { page(path, auth.token()) } catch (e: RMCException) {
            if (e.status != 401) throw e
            page(path, auth.token(true))
        }
        val video = result.optJSONArray("sections").objects().mapNotNull { it.optJSONObject("video") }.firstOrNull()
            ?: throw IOException("No compatible RMC+ replay stream is available.")
        val url = video.text("url") ?: throw IOException("RMC+ returned no manifest.")
        require(url.toHttpUrl().scheme == "https") { "Invalid RMC+ manifest." }
        RMCStream(url, video.text("format") == "dash", video.optJSONObject("drm")?.text("play_token"))
    }
}
