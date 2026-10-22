package fr.tv5mondeplus

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TV5Provider : MainAPI() {
    override var name = "TV5MONDEplus"
    override var mainUrl = TV5Client.SITE
    override var lang = "fr"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override val hasMainPage = true
    override val hasChromecastSupport = false
    override val hasDownloadSupport = false
    override val mainPage = mainPageOf("home" to "TV5MONDEplus")
    private val client = TV5Client()
    private fun result(item: TV5Item): SearchResponse {
        val url = "$mainUrl/details/vod/redbee:${item.id}"
        return if (item.series) newTvSeriesSearchResponse(item.title, url, TvType.TvSeries) { posterUrl = item.poster }
        else newMovieSearchResponse(item.title, url, TvType.Movie) { posterUrl = item.poster }
    }
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse = withContext(Dispatchers.IO) {
        val shelves = client.home(page)
        newHomePageResponse(shelves.map { (title, items) -> HomePageList(title, items.map(::result), true) }, hasNext = shelves.isNotEmpty())
    }
    override suspend fun search(query: String): List<SearchResponse> = withContext(Dispatchers.IO) {
        if (query.isBlank()) emptyList() else client.search(query).map(::result)
    }
    override suspend fun load(url: String): LoadResponse = withContext(Dispatchers.IO) {
        val details = client.details(url.substringAfterLast(':'))
        val item = details.item
        if (!item.series) return@withContext newMovieLoadResponse(item.title, url, TvType.Movie, item.id) {
            posterUrl = item.poster; plot = item.description
        }
        newTvSeriesLoadResponse(item.title, url, TvType.TvSeries, details.episodes.mapIndexed { index, entry ->
            newEpisode(entry.id, initializer = {
                name = entry.title; posterUrl = entry.poster; description = entry.description
                season = entry.season ?: 1; episode = entry.episode ?: index + 1
            }, fix = false)
        }) { posterUrl = item.poster; plot = item.description }
    }
    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit): Boolean = withContext(Dispatchers.IO) {
        if (isCasting) throw ErrorLoadingException("TV5MONDEplus Chromecast playback is not supported yet.")
        val stream = client.playback(data)
        if (stream.license != null) callback(newDrmExtractorLink(name, "TV5MONDEplus · Widevine", stream.url, ExtractorLinkType.DASH, WIDEVINE_UUID) {
            licenseUrl = stream.license
        }) else callback(newExtractorLink(name, name, stream.url, ExtractorLinkType.DASH))
        true
    }
}
