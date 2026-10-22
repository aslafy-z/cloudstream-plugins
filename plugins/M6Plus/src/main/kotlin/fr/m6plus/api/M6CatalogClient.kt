package fr.m6plus.api

import fr.m6plus.diagnostics.M6Diagnostics
import fr.m6plus.model.M6Episode
import fr.m6plus.model.M6Program
import org.json.JSONArray
import org.json.JSONObject

internal class M6CatalogClient(private val http: M6ApiClient) {
    private val base = "https://android.middleware.6play.fr/6play/v2/platforms/m6group_androidmob/services/6play"
    private val headers = mapOf("x-customer-name" to "m6web")
    private val cacheMs = 10 * 60 * 1000L

    fun categories(channel: String): List<Pair<String, String>> = M6Diagnostics.trace(M6Diagnostics.Operation.CATEGORIES) {
        val service = if (channel == "gulli") "gulli" else "${channel}replay"
        val url = "https://android.middleware.6play.fr/6play/v2/platforms/m6group_androidmob/services/$service/folders?limit=999&offset=0"
        return@trace JSONArray(http.get(url, headers, cacheMs)).objects().mapNotNull { item ->
            val id = item.text("id") ?: return@mapNotNull null
            id to (item.text("name") ?: return@mapNotNull null)
        }
    }

    fun programs(categoryId: String): List<M6Program> = M6Diagnostics.trace(M6Diagnostics.Operation.PROGRAMS) {
        val url = "$base/folders/${M6ApiClient.encode(categoryId)}/programs?limit=999&offset=0&csa=6&with=parentcontext"
        return@trace pages(url).mapNotNull(::program)
    }

    fun program(id: String): Pair<M6Program, List<Pair<String, String>>> = M6Diagnostics.trace(M6Diagnostics.Operation.PROGRAM) {
        val url = "$base/programs/${M6ApiClient.encode(id)}?with=links,subcats,rights"
        val body = JSONObject(http.get(url, headers, cacheMs))
        val details = program(body) ?: throw IllegalStateException("M6+ programme not found.")
        val subcats = body.optJSONArray("program_subcats")?.objects().orEmpty().mapNotNull {
            val subcatId = it.text("id") ?: return@mapNotNull null
            subcatId to (it.text("title") ?: "Season")
        }
        return@trace details to subcats
    }

    fun episodes(programId: String, subcats: List<Pair<String, String>>): List<M6Episode> = M6Diagnostics.trace(M6Diagnostics.Operation.EPISODES) {
        val all = linkedMapOf<String, M6Episode>()
        val genericUrl = "$base/programs/${M6ApiClient.encode(programId)}/videos?csa=6&with=clips,freemiumpacks&type=vi,vc,playlist&limit=999&offset=0"
        addEpisodes(all, pages(genericUrl), null)
        if (all.values.any { it.season != null }) return@trace orderedEpisodes(all.values)
        for ((id, title) in subcats) {
            val season = Regex("(?i)saison\\s*(\\d+)").find(title)?.groupValues?.getOrNull(1)?.toIntOrNull()
            if (season == null) continue
            val url = "$base/programs/${M6ApiClient.encode(programId)}/videos?csa=6&with=clips,freemiumpacks&type=vi,vc,playlist&limit=999&offset=0&subcat=${M6ApiClient.encode(id)}&sort=subcat"
            try { addEpisodes(all, pages(url), season) }
            catch (_: M6ApiException) { M6Diagnostics.record(M6Diagnostics.Event.SEASON_FALLBACK) }
        }
        return@trace orderedEpisodes(all.values)
    }

    // The middleware caps pages at 100 even when callers ask for limit=999.
    private fun pages(url: String): List<JSONObject> {
        val items = linkedMapOf<String, JSONObject>()
        var offset = 0
        while (true) {
            val pageUrl = url.replace("limit=999", "limit=100").replace("offset=0", "offset=$offset")
            val page = JSONArray(http.get(pageUrl, headers, cacheMs)).objects()
            if (page.isEmpty()) break
            val before = items.size
            for (item in page) {
                val id = item.text("id") ?: throw IllegalStateException("Missing M6+ catalogue identifier.")
                items[id] = item
            }
            if (page.size < 100) break
            if (items.size == before) throw IllegalStateException("M6+ catalogue pagination stopped advancing.")
            offset += page.size
        }
        return items.values.toList()
    }

    private fun addEpisodes(target: MutableMap<String, M6Episode>, videos: List<JSONObject>, season: Int?) {
        for (video in videos) {
            val title = video.text("title") ?: "Video"
            val clips = video.optJSONArray("clips")?.objects().orEmpty()
            if (video.text("type") == "playlist" && clips.isNotEmpty()) {
                for (clip in clips) {
                    val id = clip.text("video_id") ?: continue
                    val item = episode(id, clip.text("title") ?: title, video, clip, season)
                    target[id] = target[id]?.let { if (it.season == null) item else it } ?: item
                }
            } else {
                val id = video.text("id") ?: continue
                val item = episode(id, title, video, clips.firstOrNull(), season)
                target[id] = target[id]?.let { if (it.season == null) item else it } ?: item
            }
        }
    }

    private fun episode(id: String, title: String, video: JSONObject, clip: JSONObject?, season: Int?): M6Episode {
        val product = clip?.optJSONObject("product") ?: video.optJSONObject("product")
        return M6Episode(id, title, clip?.let(::image) ?: image(video),
            clip?.text("description") ?: video.text("description"),
            product?.optInt("season")?.takeIf { it > 0 } ?: season,
            episode = product?.optInt("episode")?.takeIf { it > 0 },
            part = product?.optInt("part_number")?.takeIf { it > 0 },
            airedAt = product?.text("first_diffusion") ?: product?.text("first_local_air_date")
                ?: video.text("publication_date"),
            isExtract = video.text("type") == "vc" || clip?.text("type") == "vc")
    }

    private fun orderedEpisodes(items: Collection<M6Episode>): List<M6Episode> = items.groupBy { it.season }
        .entries.sortedBy { it.key ?: 0 }.flatMap { (_, season) ->
            val sorted = season.sortedWith(compareBy<M6Episode> { it.isExtract }
                .thenBy { it.episode ?: Int.MAX_VALUE }.thenBy { it.part ?: 1 }
                .thenBy { it.airedAt ?: "9999" })
            val main = sorted.filterNot { it.isExtract }
            val numbered = main.mapNotNull { it.episode }
            val keepOriginalNumbers = main.isNotEmpty() && numbered.size == main.size && numbered.distinct().size == main.size
            var next = if (keepOriginalNumbers) numbered.max() + 1 else 1
            sorted.map { item ->
                // CloudStream drops duplicate season/episode pairs. Split parts need distinct numbers.
                item.copy(displayNumber = if (keepOriginalNumbers && !item.isExtract) item.episode!! else next++)
            }
        }

    private fun program(json: JSONObject): M6Program? {
        val id = json.text("id") ?: return null
        return M6Program(id, json.text("title") ?: json.text("name") ?: return null,
            image(json), json.text("description"))
    }

    private fun image(json: JSONObject): String? = json.optJSONArray("images")?.objects()
        ?.firstOrNull { it.text("role") in setOf("vignette", "carousel", "poster") }
        ?.text("external_key")?.let { "https://images.6play.fr/v1/images/${M6ApiClient.encode(it)}/raw" }
}
