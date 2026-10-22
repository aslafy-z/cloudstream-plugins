package fr.tf1plus

import org.json.JSONArray
import org.json.JSONObject

/** SmartTV editorial lists expose seasonal replays absent from the flat replay query. */
internal fun TF1Catalog.videosWithSeasons(slug: String, id: String): List<TF1Video> =
    TF1Diagnostics.trace(TF1Diagnostics.Operation.EPISODES) {
        val seasons = try {
            TF1Diagnostics.trace(TF1Diagnostics.Operation.EDITORIAL) { editorialVideos(slug, id) }
        } catch (_: Exception) {
            TF1Diagnostics.record(TF1Diagnostics.Event.EDITORIAL_FALLBACK)
            emptyList()
        }
        if (seasons.isNotEmpty()) seasons else videos(slug)
    }

internal fun TF1Catalog.editorialVideos(slug: String, id: String): List<TF1Video> {
    val url = TF1Http.query("https://smart-tv.tf1.fr/catalog/fr-fr/smarttv", mapOf(
        "id" to "379fec96081ab1a2",
        "variables" to JSONObject().put("programId", id).toString()
    ))
    val raw = JSONObject(httpGet(url)).objectAt("data", "programById") ?: return emptyList()
    val sections = raw.optJSONArray("editorialSections") ?: return emptyList()
    val result = linkedMapOf<String, TF1Video>()
    for (i in 0 until sections.length()) {
        val lists = sections.optJSONObject(i)?.optJSONArray("lists") ?: continue
        for (j in 0 until lists.length()) {
            val list = lists.optJSONObject(j) ?: continue
            val listId = list.stringOrNull("id") ?: continue
            val label = list.optJSONObject("decoration")?.stringOrNull("label").orEmpty()
            val season = Regex("(?i)(?:saison|season)\\s*(\\d+)").find(label)
                ?.groupValues?.getOrNull(1)?.toIntOrNull()
            var offset = 0
            val total = list.optInt("total", 0).coerceAtMost(1000)
            while (offset < total) {
                val size = minOf(50, total - offset)
                val variables = JSONObject().put("programSlug", slug).put("listId", listId)
                    .put("offset", offset).put("limit", size)
                val pageUrl = TF1Http.query("https://www.tf1.fr/graphql/web", mapOf(
                    "id" to "76bc5c35edb166fa637a6c6686cb05c7140d2a8b",
                    "variables" to variables.toString()
                ))
                val response = JSONObject(httpGet(pageUrl))
                val editorial = response.objectAt("data", "programBySlug")
                    ?.optJSONArray("editorialSections") ?: break
                val page = mutableListOf<TF1Video>()
                for (k in 0 until editorial.length()) {
                    val items = editorial.optJSONObject(k)?.optJSONObject("listById")
                        ?.optJSONArray("items") ?: continue
                    page += TF1Mappings.videos(items)
                }
                if (page.isEmpty()) break
                page.forEach { video ->
                    val item = video.copy(season = video.season ?: season)
                    result.putIfAbsent(item.id, item)
                }
                offset += page.size
                if (page.size < size) break
            }
        }
    }
    return result.values.toList()
}
