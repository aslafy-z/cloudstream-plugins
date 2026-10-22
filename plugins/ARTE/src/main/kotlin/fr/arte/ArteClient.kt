package fr.arte

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

internal data class ArteItem(val id: String, val title: String, val collection: Boolean,
    val poster: String?, val description: String?, val season: Int?, val episode: Int?)
internal data class ArteStream(val url: String, val label: String, val quality: Int)
internal data class ArteDetails(val title: String, val poster: String?, val description: String?, val episodes: List<ArteItem>)

internal class ArteClient(private val http: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build(),
    private val base: String = "https://api.arte.tv/api/") {
    private val cache = LinkedHashMap<String, Pair<Long, String>>()

    private fun get(path: String, catalogue: Boolean = true): JSONObject {
        val url = if (path.startsWith("https://") || path.startsWith("http://")) path else base + path
        val parsed = url.toHttpUrl()
        val origin = base.toHttpUrl()
        require(parsed.host == origin.host && parsed.port == origin.port && parsed.scheme == origin.scheme) {
            "ARTE returned an unexpected catalogue address."
        }
        if (catalogue) synchronized(cache) {
            cache[url]?.takeIf { System.nanoTime() - it.first < TimeUnit.MINUTES.toNanos(10) }
                ?.let { return JSONObject(it.second) }
        }
        val request = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0")
        // The public catalogue token must not be sent to the separate player API.
        if (catalogue) request.header("Accept", "application/vnd.api+json")
            .header("Authorization", CATALOGUE_TOKEN)
        val body = http.newCall(request.build()).execute().use {
            if (!it.isSuccessful) throw IOException("ARTE request failed (HTTP ${it.code}).")
            it.body?.string() ?: throw IOException("ARTE returned an empty response.")
        }
        if (catalogue) synchronized(cache) {
            if (cache.size >= 80) cache.remove(cache.keys.first())
            cache[url] = System.nanoTime() to body
        }
        return JSONObject(body)
    }

    fun home(category: String): List<Pair<String, List<ArteItem>>> {
        val page = get("emac/v4/fr/tv/pages/$category")
        return page.optJSONArray("zones").objects().mapNotNull { zone ->
            val items = items(zone.optJSONObject("content"))
            if (items.isEmpty()) null else zone.optString("title", "ARTE") to items
        }
    }

    fun search(query: String): List<ArteItem> {
        val url = (base + "emac/v4/fr/app/pages/SEARCH").toHttpUrl().newBuilder()
            .addQueryParameter("query", query).build().toString()
        return get(url).optJSONArray("zones").objects()
            .flatMap { items(it.optJSONObject("content")) }.distinctBy { it.id }
    }

    fun details(id: String): ArteDetails {
        require(ID.matches(id)) { "Invalid ARTE programme identifier." }
        val collection = id.startsWith("RC-")
        val page = get("emac/v4/fr/tv/${if (collection) "collections" else "programs"}/$id")
        val metadata = page.optJSONObject("metadata") ?: JSONObject()
        val zones = page.optJSONArray("zones").objects()
        val hasPrimaryVideos = zones.any { it.optString("code").startsWith("collection_videos_") && items(it.optJSONObject("content")).any { item -> !item.collection } }
        val videos = mutableListOf<ArteItem>()
        for (zone in zones) {
            val code = zone.optString("code")
            // Recommendations and trailers must never become collection episodes.
            if (collection && !code.startsWith("collection_videos_") && !code.startsWith("collection_subcollection_")) continue
            if (collection && hasPrimaryVideos && code.startsWith("collection_subcollection_")) continue
            if (!collection && !code.startsWith("program_content_")) continue
            var content = zone.optJSONObject("content") ?: continue
            val visited = mutableSetOf<String>()
            var count = 0
            while (true) {
                videos += items(content).filter { !it.collection }
                val next = content.optJSONObject("pagination")?.optJSONObject("links")?.text("next") ?: break
                check(visited.add(next) && ++count <= 100) { "ARTE catalogue pagination did not finish." }
                content = get(next)
            }
        }
        val episodes = videos.distinctBy { it.id }
        if (episodes.isEmpty()) throw IOException("No available replays in this ARTE programme.")
        return ArteDetails(metadata.text("title") ?: episodes.first().title,
            metadata.optJSONObject("og")?.optJSONObject("image")?.text("url") ?: episodes.first().poster,
            metadata.text("description"), episodes)
    }

    fun playback(id: String): List<ArteStream> {
        require(ID.matches(id) && !id.startsWith("RC-")) { "Invalid ARTE video identifier." }
        return streams(get("player/v2/config/fr/$id", catalogue = false))
    }

    companion object {
        private val ID = Regex("(?:RC-[0-9]+|[0-9]{6}-[0-9]{3}-[A-Z])")
        // Public client identifier used by ARTE's catalogue, not an account credential.
        private const val CATALOGUE_TOKEN = "Bearer YTEwZWE3M2UxMTVmYmRjZmE0YTdmNjA4ZTI2NDczZDU3YjdjYmVmMmRmNGFjOTM3M2RhNTM5ZjIxYmI3NTc1Zg"

        fun items(content: JSONObject?): List<ArteItem> = content?.optJSONArray("data").objects().mapNotNull { item ->
            val id = item.text("programId") ?: item.text("deeplink")?.substringAfterLast('/') ?: return@mapNotNull null
            if (!ID.matches(id)) return@mapNotNull null
            val title = item.text("title") ?: return@mapNotNull null
            val collection = id.startsWith("RC-")
            if (!collection && item.optJSONObject("availability")?.optBoolean("hasVideoStreams", true) == false) return@mapNotNull null
            val episode = item.optJSONObject("episodeInfo")
            ArteItem(id, listOfNotNull(title, item.text("subtitle")).distinct().joinToString(" - "), collection,
                item.optJSONObject("mainImage")?.text("url")?.replace("__SIZE__", "940x530"),
                item.text("shortDescription") ?: item.text("teaserText"),
                episode?.optInt("season")?.takeIf { it > 0 }, episode?.optInt("episode")?.takeIf { it > 0 })
        }

        fun streams(response: JSONObject): List<ArteStream> {
            val attributes = response.optJSONObject("data")?.optJSONObject("attributes")
                ?: throw IOException("ARTE returned an invalid player configuration.")
            val streams = attributes.optJSONArray("streams").objects().mapNotNull { stream ->
                val url = stream.text("url") ?: return@mapNotNull null
                if (!url.startsWith("https://") || !url.substringBefore('?').endsWith(".m3u8")) return@mapNotNull null
                val versions = stream.optJSONArray("versions").objects()
                val labels = versions.mapNotNull { it.text("label") }.distinct().joinToString(" / ")
                ArteStream(url, labels.ifEmpty { "ARTE" }, stream.optJSONObject("mainQuality")?.optString("label")
                    ?.filter { it.isDigit() }?.toIntOrNull() ?: 0)
            }.distinctBy { it.url }
            if (streams.isEmpty()) throw IOException("No compatible ARTE stream is available. Check programme availability and your region.")
            return streams
        }
    }
}

internal fun JSONObject.text(key: String): String? = if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
internal fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
