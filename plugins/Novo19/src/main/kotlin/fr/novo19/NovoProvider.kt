package fr.novo19

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NovoProvider : MainAPI() {
    override var name = "NOVO19"
    override var mainUrl = NovoClient.SITE
    override var lang = "fr"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = false
    override val mainPage = mainPageOf("home" to "NOVO19")
    private val client get() = NovoRuntime.client
    private fun result(item: NovoItem): SearchResponse {
        val url = item.url
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
        val details = client.details(url)
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
        val stream = client.playback(data)
        // Unprotected DASH needs no headers, so the standard receiver can fetch it directly.
        if ((isCasting || NovoRuntime.castConnected) && stream.license != null) {
            val url = try { NovoRuntime.cast.link(stream) }
                catch (error: CancellationException) { throw error }
                catch (error: Exception) {
                    val message = "The NOVO19 Chromecast relay failed: ${error.message ?: "unknown error"}"
                    NovoRuntime.castMessage(message)
                    throw ErrorLoadingException(message)
                }
            // CloudStream shares cached links between local playback and Cast.
            callback(newDrmExtractorLink(name, "NOVO19 · Direct Chromecast", url, ExtractorLinkType.DASH, WIDEVINE_UUID) {
                licenseUrl = url.substringBeforeLast('/') + "/license"
            })
            return@withContext true
        }
        if (stream.license != null) callback(newDrmExtractorLink(name, "NOVO19 · Widevine", stream.url, ExtractorLinkType.DASH, WIDEVINE_UUID) {
            licenseUrl = stream.license
        }) else callback(newExtractorLink(name, name, stream.url, ExtractorLinkType.DASH))
        true
    }
}
