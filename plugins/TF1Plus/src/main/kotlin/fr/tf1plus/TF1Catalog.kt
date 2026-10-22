package fr.tf1plus

import org.json.JSONArray
import org.json.JSONObject

internal data class TF1Programme(
    val id: String, val slug: String, val title: String, val poster: String?,
    val description: String?, val categories: Set<String> = emptySet()
)
internal data class TF1Video(
    val id: String, val title: String, val poster: String?, val description: String?,
    val season: Int? = null, val number: Int? = null
)
internal data class TF1Category(val id: String, val title: String)

internal object TF1Mappings {
    fun image(json: JSONObject?, key: String): String? = imageSources(json?.optJSONObject(key))

    private fun imageSources(image: JSONObject?): String? =
        (image?.optJSONArray("sources")?.jsonObjects().orEmpty() +
            image?.optJSONArray("sourcesWithScales")?.jsonObjects().orEmpty())
            .mapNotNull { it.stringOrNull("url") }
            .filter { it.startsWith("https://") && !it.substringBefore('?').endsWith(".mp4", true) }
            .sortedBy { url ->
                val path = url.substringBefore('?').lowercase()
                when {
                    path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".png") -> 0
                    path.endsWith(".avif") -> 2
                    else -> 1
                }
            }.firstOrNull()

    fun programmes(array: JSONArray?): List<TF1Programme> = array?.jsonObjects().orEmpty().mapNotNull { item ->
        val id = item.stringOrNull("id") ?: return@mapNotNull null
        val slug = item.stringOrNull("slug") ?: return@mapNotNull null
        val title = item.stringOrNull("name") ?: return@mapNotNull null
        val decor = item.optJSONObject("decoration")
        TF1Programme(id, slug, title, image(decor, "image"), decor?.stringOrNull("description"),
            item.optJSONArray("categories")?.jsonObjects().orEmpty()
                .mapNotNull { it.stringOrNull("id") }.toSet())
    }

    fun videos(array: JSONArray?): List<TF1Video> = array?.jsonObjects().orEmpty().mapNotNull { item ->
        val video = item.optJSONObject("video") ?: item
        val id = video.stringOrNull("id") ?: return@mapNotNull null
        val decor = video.optJSONObject("decoration")
        val title = decor?.stringOrNull("label") ?: return@mapNotNull null
        val images = decor?.optJSONArray("images")
        val poster = image(item, "image") ?: images?.jsonObjects().orEmpty()
            .sortedBy { if (it.stringOrNull("type") == "THUMBNAIL_LARGE") 0 else 1 }
            .firstNotNullOfOrNull(::imageSources)
        val season = video.optInt("season").takeIf { it > 0 } ?: Regex("(?i)(?:saison|season|s)\\s*[.: -]?\\s*(\\d+)").find(title)
            ?.groupValues?.getOrNull(1)?.toIntOrNull()
        val episode = video.optInt("episode").takeIf { it > 0 } ?: Regex("(?i)(?:épisode|episode|ep|e)\\s*[.: -]?\\s*(\\d+)").find(title)
            ?.groupValues?.getOrNull(1)?.toIntOrNull()
        TF1Video(id, title, poster, decor?.stringOrNull("summary") ?: decor?.stringOrNull("description"),
            season, episode)
    }
}

internal class TF1Catalog(private val http: TF1Http) {
    private val graphql = "https://www.tf1.fr/graphql/web"
    private val cacheMs = 10 * 60 * 1000L

    private fun graph(id: String, vars: JSONObject): JSONObject {
        val url = TF1Http.query(graphql, mapOf("id" to id, "variables" to vars.toString()))
        val response = JSONObject(http.get(url, cacheMs = cacheMs))
        if (response.optJSONObject("data") == null && (response.optJSONArray("errors")?.length() ?: 0) > 0) {
            throw IllegalStateException("TF1+ catalogue query rejected (persisted query may have changed).")
        }
        return response.optJSONObject("data") ?: throw IllegalStateException("Invalid TF1+ catalogue response.")
    }

    internal fun httpGet(url: String): String = http.get(url, cacheMs = cacheMs)

    fun categories(): List<TF1Category> = TF1Diagnostics.trace(TF1Diagnostics.Operation.CATEGORIES) {
        val data = graph("909c68c0", JSONObject())
        return@trace data.keys().asSequence().mapNotNull { key -> data.optJSONObject(key) }
            .mapNotNull { item ->
                val id = item.stringOrNull("id") ?: return@mapNotNull null
                TF1Category(id, item.stringOrNull("label") ?: return@mapNotNull null)
            }.toList()
    }

    fun programmes(channel: String, page: Int, limit: Int = 60): List<TF1Programme> = TF1Diagnostics.trace(TF1Diagnostics.Operation.PROGRAMS) {
        val vars = JSONObject()
            .put("context", JSONObject().put("persona", "PERSONA_2").put("application", "WEB")
                .put("device", "DESKTOP").put("os", "WINDOWS"))
            .put("filter", JSONObject().put("channel", if (channel == "all") "" else channel))
            .put("offset", page.coerceAtLeast(0)).put("limit", limit)
        return@trace TF1Mappings.programmes(graph("483ce0f", vars)
            .objectAt("programs")?.optJSONArray("items"))
    }

    fun searchProgrammes(query: String): List<TF1Programme> =
        TF1Diagnostics.trace(TF1Diagnostics.Operation.SEARCH_PROGRAMS) {
            TF1Mappings.programmes(graph("e78b188",
                JSONObject().put("query", query).put("offset", 0).put("limit", 100))
                .objectAt("searchPrograms")?.optJSONArray("items"))
        }

    fun searchVideos(query: String): List<TF1Video> =
        TF1Diagnostics.trace(TF1Diagnostics.Operation.SEARCH_VIDEOS) {
            TF1Mappings.videos(graph("b2dc9439",
                JSONObject().put("query", query).put("offset", 0).put("limit", 100))
                .objectAt("searchVideos")?.optJSONArray("items"))
        }

    fun programme(id: String): TF1Programme? = TF1Diagnostics.trace(TF1Diagnostics.Operation.PROGRAM) {
        val vars = JSONObject().put("programId", id)
        val url = TF1Http.query("https://smart-tv.tf1.fr/catalog/fr-fr/smarttv",
            mapOf("id" to "379fec96081ab1a2", "variables" to vars.toString()))
        val data = JSONObject(http.get(url, cacheMs = cacheMs))
            .objectAt("data", "programById") ?: return@trace null
        val decor = data.optJSONObject("decoration")
        val poster = TF1Mappings.image(decor, "image")
        return@trace TF1Programme(id, data.stringOrNull("slug") ?: "",
            data.stringOrNull("name") ?: decor?.stringOrNull("label") ?: "TF1+",
            poster, decor?.stringOrNull("description"))
    }

    fun videos(slug: String, type: String = "REPLAY"): List<TF1Video> = TF1Diagnostics.trace(TF1Diagnostics.Operation.EPISODES) {
        val result = linkedMapOf<String, TF1Video>()
        var page = 0
        while (true) {
            val vars = JSONObject().put("programSlug", slug).put("offset", page)
                .put("limit", 20).put("sort", JSONObject().put("type", "DATE").put("order", "DESC"))
                .put("types", JSONArray().put(type))
            val connection = graph("a6f9cf0e", vars).objectAt("programBySlug", "videos")
            val raw = connection?.optJSONArray("items")
            val items = TF1Mappings.videos(raw)
            val before = result.size
            items.forEach { result[it.id] = it }
            if ((raw?.length() ?: 0) < 20 || result.size >= (connection?.optInt("total", Int.MAX_VALUE) ?: Int.MAX_VALUE)) break
            check(result.size > before) { "TF1+ repeated a replay page." }
            page++
            check(page < 500) { "TF1+ replay catalogue is too large to load." }
        }
        return@trace result.values.toList()
    }
}
