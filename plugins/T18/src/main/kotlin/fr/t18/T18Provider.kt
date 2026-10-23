package fr.t18

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class T18Provider : MainAPI() {
    override var name = "T18"
    override var mainUrl = T18Client.SITE
    override var lang = "fr"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = false
    override val mainPage = mainPageOf("home" to "T18")
    private val client = T18Client()
    private fun result(item: T18Item): SearchResponse = newTvSeriesSearchResponse(item.title, item.url, TvType.TvSeries) { posterUrl = item.poster }
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse = withContext(Dispatchers.IO) {
        val shelves = client.home(page)
        newHomePageResponse(shelves.map { (title, items) -> HomePageList(title, items.map(::result), true) }, hasNext = shelves.isNotEmpty())
    }
    override suspend fun search(query: String): List<SearchResponse> = withContext(Dispatchers.IO) {
        if (query.isBlank()) emptyList() else client.search(query).map(::result)
    }
    override suspend fun load(url: String): LoadResponse = withContext(Dispatchers.IO) {
        val details = client.details(url)
        val item = details.item
        if (details.direct) return@withContext newMovieLoadResponse(item.title, url, TvType.Movie, item.url) {
            posterUrl = item.poster; plot = item.description
        }
        newTvSeriesLoadResponse(item.title, url, TvType.TvSeries, details.episodes.mapIndexed { index, entry ->
            newEpisode(entry.url, initializer = {
                name = entry.title; posterUrl = entry.poster; description = entry.description
                season = 1; episode = index + 1
            }, fix = false)
        }) { posterUrl = item.poster; plot = item.description }
    }
    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit): Boolean = withContext(Dispatchers.IO) {
        val stream = client.playback(data)
        // Dailymotion sidecar subtitles are SRT, which Cast receivers cannot render; the HLS master carries WebVTT renditions.
        if (!isCasting) stream.subtitles.forEach { (language, url) -> subtitleCallback(newSubtitleFile(language, url) { headers = T18Client.MEDIA_HEADERS }) }
        callback(newExtractorLink(name, name, stream.url, ExtractorLinkType.M3U8) { headers = T18Client.MEDIA_HEADERS })
        true
    }
}
