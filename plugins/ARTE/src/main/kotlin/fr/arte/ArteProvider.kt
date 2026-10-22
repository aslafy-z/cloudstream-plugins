package fr.arte

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ArteProvider : MainAPI() {
    override var name = "ARTE"
    override var mainUrl = "https://www.arte.tv"
    override var lang = "fr"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = false
    override val mainPage = mainPageOf("HOME" to "ARTE")
    private val client = ArteClient()

    private fun result(item: ArteItem): SearchResponse {
        val url = "$mainUrl/fr/videos/${item.id}/"
        return if (item.collection) newTvSeriesSearchResponse(item.title, url, TvType.TvSeries) { posterUrl = item.poster }
        else newMovieSearchResponse(item.title, url, TvType.Movie) { posterUrl = item.poster }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse = withContext(Dispatchers.IO) {
        newHomePageResponse(client.home(request.data).map { (title, items) ->
            HomePageList(title, items.map(::result), true)
        }, hasNext = false)
    }

    override suspend fun search(query: String): List<SearchResponse> = withContext(Dispatchers.IO) {
        if (query.isBlank()) emptyList() else client.search(query).map(::result)
    }

    override suspend fun load(url: String): LoadResponse = withContext(Dispatchers.IO) {
        val id = Regex("/videos/([^/]+)").find(url)?.groupValues?.get(1)
            ?: throw ErrorLoadingException("Invalid ARTE programme URL.")
        val details = client.details(id)
        if (id.startsWith("RC-")) {
            newTvSeriesLoadResponse(details.title, url, TvType.TvSeries, details.episodes.mapIndexed { index, item ->
                newEpisode(item.id, initializer = {
                    name = item.title; posterUrl = item.poster; description = item.description
                    season = item.season ?: 1; episode = item.episode ?: index + 1
                }, fix = false)
            }) { posterUrl = details.poster; plot = details.description }
        } else newMovieLoadResponse(details.title, url, TvType.Movie, id) {
            posterUrl = details.poster; plot = details.description
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit): Boolean = withContext(Dispatchers.IO) {
        client.playback(data).forEach { stream ->
            // Keep the master playlist so the player can select audio and subtitle tracks.
            callback(newExtractorLink(name, stream.label, stream.url, ExtractorLinkType.M3U8) {
                quality = stream.quality
            })
        }
        true
    }
}
