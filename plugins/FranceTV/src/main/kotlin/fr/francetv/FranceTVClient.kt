package fr.francetv

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

internal data class FTVItem(val id: String, val title: String, val program: Boolean,
    val poster: String?, val description: String?, val season: Int?, val episode: Int?)
internal data class FTVDetails(val item: FTVItem, val episodes: List<FTVItem>)
internal data class FTVPlayback(val url: String, val dash: Boolean, val license: String?, val headers: Map<String, String>)

internal class FranceTVClient(private val http: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build(),
    private val catalogue: String = "https://api-mobile.yatta.francetv.fr/") {
    private val cache = LinkedHashMap<String, Pair<Long, String>>()
    private fun json(url: String, body: JSONObject? = null, cached: Boolean = false): JSONObject {
        if (cached) synchronized(cache) {
            cache[url]?.takeIf { System.nanoTime() - it.first < TimeUnit.MINUTES.toNanos(10) }
                ?.let { return JSONObject(it.second) }
        }
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT)
        if (body != null) request.header("Origin", "https://www.france.tv")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
        val text = http.newCall(request.build()).execute().use {
            if (!it.isSuccessful) throw IOException("france.tv request failed (HTTP ${it.code}).")
            it.body?.string() ?: throw IOException("france.tv returned an empty response.")
        }
        if (cached) synchronized(cache) {
            if (cache.size >= 80) cache.remove(cache.keys.first())
            cache[url] = System.nanoTime() to text
        }
        return JSONObject(text)
    }
    private fun catalog(path: String, page: Int = 0): JSONObject {
        val url = (catalogue + path).toHttpUrl().newBuilder()
            .addQueryParameter("platform", "apps").addQueryParameter("page", page.toString()).build()
        return json(url.toString(), cached = true)
    }
    fun home(channel: String): List<Pair<String, List<FTVItem>>> {
        require(channel in CHANNELS)
        return catalog("apps/channels/$channel").optJSONArray("collections").objects().mapNotNull { shelf ->
            val items = shelf.optJSONArray("items").objects().mapNotNull(::item)
            if (items.isEmpty()) null else (shelf.text("label") ?: "france.tv") to items
        }
    }
    fun search(query: String): List<FTVItem> {
        val url = (catalogue + "apps/search").toHttpUrl().newBuilder().addQueryParameter("platform", "apps")
            .addQueryParameter("filters", "with-collections").addQueryParameter("term", query).build()
        return json(url.toString(), cached = true).optJSONArray("collections").objects()
            .flatMap { it.optJSONArray("items").objects().mapNotNull(::item) }.distinctBy { it.id }
    }
    fun details(id: String): FTVDetails {
        require(Regex("[a-zA-Z0-9_-]+").matches(id))
        val first = catalog("apps/program/$id")
        val program = item(first.getJSONObject("item")) ?: throw IOException("france.tv programme is unavailable.")
        val videos = mutableListOf<FTVItem>()
        val collections = first.optJSONArray("collections").objects()
        for ((position, shelf) in collections.withIndex()) {
            if (shelf.optString("type") != "playlist_video") continue
            var current = shelf
            val visited = mutableSetOf<Int>()
            while (true) {
                videos += current.optJSONArray("items").objects().mapNotNull(::item).filter { !it.program }
                val next = current.optJSONObject("cursor")?.optInt("next", -1) ?: -1
                if (next <= 0) break
                check(visited.add(next) && visited.size <= 100) { "france.tv pagination did not finish." }
                val response = catalog("apps/program/$id", next)
                current = response.optJSONArray("collections")?.optJSONObject(position) ?: break
            }
        }
        val episodes = videos.distinctBy { it.id }.sortedWith(compareBy<FTVItem> { it.season ?: 1 }.thenBy { it.episode ?: Int.MAX_VALUE })
        if (episodes.isEmpty()) throw IOException("No available replays in this france.tv programme.")
        return FTVDetails(program, episodes)
    }
    fun video(id: String): JSONObject {
        require(UUID.matches(id)) { "Invalid france.tv video identifier." }
        val url = "https://k7.ftven.fr/videos/$id".toHttpUrl().newBuilder()
            .addQueryParameter("capabilities", "drm").addQueryParameter("os", "androidtv")
            .addQueryParameter("diffusion_mode", "tunnel_first").addQueryParameter("offline", "false").build()
        return json(url.toString())
    }
    fun playback(id: String): FTVPlayback {
        val value = video(id).optJSONObject("video") ?: throw IOException("This france.tv replay is unavailable in your region.")
        val source = value.text("url") ?: throw IOException("france.tv did not return a stream.")
        val format = value.optString("format")
        check(format.contains("dash") || format.contains("hls")) { "Unsupported france.tv stream format." }
        val authUrl = "https://hdfauth.ftven.fr/esi/TA".toHttpUrl().newBuilder()
            .addQueryParameter("format", "json").addQueryParameter("url", source).build()
        val manifest = json(authUrl.toString()).text("url") ?: throw IOException("france.tv stream authorization failed.")
        require(manifest.startsWith("https://"))
        if (!value.optBoolean("drm")) return FTVPlayback(manifest, format.contains("dash"), null, emptyMap())
        check(format.contains("dash")) { "Encrypted HLS is not supported by this plugin." }
        val endpoint = value.getString("token").toHttpUrl()
        require(endpoint.scheme == "https" && (endpoint.host == "ftven.fr" || endpoint.host.endsWith(".ftven.fr")))
        val token = json(endpoint.toString(), JSONObject().put("id", id).put("drm_type", "widevine").put("license_type", "online"))
            .text("token") ?: throw IOException("france.tv license authorization failed.")
        return FTVPlayback(manifest, true, "https://api-drm.ftven.fr/v1/wvls/contentlicenseservice/v1/licenses/",
            mapOf("nv-authorizations" to token, "Origin" to "https://www.france.tv", "Referer" to "https://www.france.tv/", "User-Agent" to USER_AGENT))
    }
    companion object {
        val CHANNELS = setOf("france-2", "france-3", "france-4", "france-5", "franceinfo", "slash")
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"
        private val UUID = Regex("[a-fA-F0-9]{8}(?:-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}")
        fun item(value: JSONObject): FTVItem? {
            val program = value.optString("type") == "program"
            if (!program && value.optString("type") !in setOf("integrale", "unitaire", "resume")) return null
            if (!program && (value.optBoolean("is_live") || !value.optBoolean("is_published", true))) return null
            val id = value.text(if (program) "program_path" else "si_id") ?: return null
            if (!program && !UUID.matches(id)) return null
            val title = value.text(if (program) "label" else "episode_title") ?: value.text("title") ?: return null
            val images = value.optJSONArray("images").objects().sortedBy {
                when (it.optString("type")) { "vignette_16x9" -> 0; "background_16x9" -> 1; else -> 2 }
            }
            val poster = images.firstNotNullOfOrNull { image ->
                val urls = image.optJSONObject("urls")
                urls?.text("w:800") ?: urls?.text("w:400") ?: urls?.keys()?.asSequence()?.mapNotNull { urls.text(it) }?.firstOrNull()
            }
            return FTVItem(id, title, program, poster, value.text("description"),
                value.optInt("season", value.optInt("saison")).takeIf { it > 0 }, value.optInt("episode").takeIf { it > 0 })
        }
    }
}
internal fun JSONObject.text(key: String): String? = if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
internal fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
