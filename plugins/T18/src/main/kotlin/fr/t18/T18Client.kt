package fr.t18

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException
import java.util.concurrent.TimeUnit

internal data class T18Item(val url: String, val title: String, val poster: String?, val description: String? = null, val date: String? = null)
internal data class T18Details(val item: T18Item, val episodes: List<T18Item>, val direct: Boolean)
internal data class T18Stream(val url: String, val subtitles: List<Pair<String, String>>)
internal fun JSONObject.text(key: String): String? = optString(key).takeIf { it.isNotBlank() && it != "null" }
internal fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull(::optJSONObject)
internal class T18Client {
    companion object {
        const val SITE = "https://t18.fr"
        const val UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/130.0.0.0 Safari/537.36"
        val MEDIA_HEADERS = mapOf("User-Agent" to UA, "Referer" to "https://www.dailymotion.com/", "Origin" to "https://www.dailymotion.com")
        fun address(path: String): String {
            val url = SITE.toHttpUrl().resolve(path) ?: throw IOException("Invalid T18 address.")
            require(url.scheme == "https" && url.port == 443 && url.host in setOf("t18.fr", "www.t18.fr")) { "Invalid T18 address." }
            return url.toString()
        }
        fun cards(document: Document): List<T18Item> = document.select("a.product-card[href]").mapNotNull { card ->
            val image = card.selectFirst("img")
            val title = card.selectFirst(".product-card__title")?.text()?.takeIf { it.isNotBlank() }
                ?: image?.attr("alt")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val path = card.attr("href")
            if ("/prog/" !in path) return@mapNotNull null
            val poster = image?.attr("data-src")?.takeIf { it.startsWith("https://") }
                ?: image?.attr("src")?.takeIf { it.startsWith("https://") }
            T18Item(address(path), title, poster)
        }.distinctBy { it.url }
        fun replays(array: JSONArray): List<T18Item> = array.objects().mapNotNull {
            T18Item(address(it.text("url") ?: return@mapNotNull null), it.text("title") ?: return@mapNotNull null,
                it.text("imageUrl"), it.text("summary"), it.text("broadcastedAt"))
        }.distinctBy { it.url }.sortedBy { it.date.orEmpty() }
        fun videoId(html: String) = Jsoup.parse(html).selectFirst("[data-dailymotion-video-id]")?.attr("data-dailymotion-video-id")
            ?.takeIf { Regex("[A-Za-z0-9]+").matches(it) }
    }
    private val http = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()
    private val cache = LinkedHashMap<String, Pair<Long, String>>()
    private fun request(request: Request): String = http.newCall(request.newBuilder().header("User-Agent", UA).build()).execute().use {
        if (!it.isSuccessful) throw IOException("T18 request failed (HTTP ${it.code}).")
        it.body?.string() ?: throw IOException("T18 returned an empty response.")
    }
    private fun get(path: String): String {
        val url = address(path)
        synchronized(cache) { cache[url]?.takeIf { System.currentTimeMillis() - it.first < 600_000 }?.let { return it.second } }
        val raw = request(Request.Builder().url(url).header("Accept", "application/json,text/html").header("X-Requested-With", "XMLHttpRequest").build())
        synchronized(cache) { if (cache.size >= 80) cache.remove(cache.keys.first()); cache[url] = System.currentTimeMillis() to raw }
        return raw
    }
    private fun categories(): List<Pair<String, String>> = Jsoup.parse(get("/replay")).select("a[href]")
        .filter { "/cat/" in it.attr("href") && it.text().isNotBlank() && it.text() != "Tout voir" }
        .map { it.text() to address(it.attr("href")) }.distinctBy { it.second }
    fun home(page: Int) = categories().drop((page - 1).coerceAtLeast(0) * 3).take(3).mapNotNull { (title, url) ->
        val items = cards(Jsoup.parse(get(url)))
        if (items.isEmpty()) null else title to items
    }
    fun search(query: String): List<T18Item> = categories().flatMap { (_, url) -> cards(Jsoup.parse(get(url))) }
        .filter { it.title.contains(query, ignoreCase = true) }.distinctBy { it.url }
    fun details(url: String): T18Details {
        val raw = get(url)
        val doc = Jsoup.parse(raw)
        val title = doc.selectFirst("h1")?.text()?.takeIf { it.isNotBlank() } ?: "T18 replay"
        val meta = T18Item(address(url), title, doc.selectFirst("meta[property=og:image]")?.attr("content"),
            doc.selectFirst("meta[property=og:description]")?.attr("content"))
        if (videoId(raw) != null) return T18Details(meta, emptyList(), true)
        val pagination = doc.select("[data-pagination-url]").firstOrNull { "/replays" in it.attr("data-pagination-url") }
            ?: throw IOException("No available replays in this T18 programme.")
        val count = pagination.attr("data-pagination-pages-count").toIntOrNull() ?: 1
        check(count in 1..100) { "T18 returned an unsupported replay page count." }
        val endpoint = address(pagination.attr("data-pagination-url"))
        val episodes = (1..count).flatMap { number ->
            val page = endpoint.toHttpUrl().newBuilder().setQueryParameter("p", number.toString()).build().toString()
            replays(JSONArray(get(page)))
        }.distinctBy { it.url }.sortedBy { it.date.orEmpty() }
        if (episodes.isEmpty()) throw IOException("No available replays in this T18 programme.")
        return T18Details(meta, episodes, false)
    }
    fun playback(url: String): T18Stream {
        val id = videoId(get(url)) ?: throw IOException("T18 returned no compatible Dailymotion video.")
        val metadata = "https://www.dailymotion.com/player/metadata/video/$id".toHttpUrl().newBuilder()
            .addQueryParameter("embedder", address(url)).build()
        val json = JSONObject(request(Request.Builder().url(metadata).header("Referer", address(url)).build()))
        if (json.has("error")) throw IOException("This T18 replay is unavailable on Dailymotion.")
        val qualities = json.optJSONObject("qualities") ?: throw IOException("Dailymotion returned no replay stream.")
        val streams = qualities.keys().asSequence().flatMap { qualities.optJSONArray(it).objects().asSequence() }
        val manifest = streams.firstOrNull { it.text("type") in setOf("application/x-mpegURL", "application/vnd.apple.mpegurl") }
            ?.text("url") ?: throw IOException("No compatible T18 HLS stream is available.")
        require(manifest.toHttpUrl().scheme == "https") { "Invalid T18 manifest address." }
        val subs = json.optJSONObject("subtitles")?.optJSONObject("data")
        val subtitles = subs?.keys()?.asSequence()?.mapNotNull { lang ->
            val item = subs.optJSONObject(lang) ?: return@mapNotNull null
            val tracks = item.optJSONArray("urls") ?: return@mapNotNull null
            val link = tracks.optString(0).takeIf { it.startsWith("https://") } ?: return@mapNotNull null
            (item.text("label") ?: lang) to link
        }?.toList().orEmpty()
        return T18Stream(manifest, subtitles)
    }
}
