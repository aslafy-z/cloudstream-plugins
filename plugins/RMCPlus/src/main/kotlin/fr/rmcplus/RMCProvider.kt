package fr.rmcplus

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.Collections
import java.util.WeakHashMap

class RMCProvider : MainAPI() {
    override var name = "RMC+"
    override var mainUrl = RMCClient.SITE
    override var lang = "fr"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override val hasMainPage = true
    override val hasChromecastSupport = false
    override val hasDownloadSupport = false
    override val mainPage = mainPageOf("accueil" to "RMC+", "rmc-story" to "RMC Story", "rmc-decouverte" to "RMC Découverte", "rmc-life" to "RMC Life")
    private val client get() = RMCRuntime.client
    private val interceptors = Collections.synchronizedMap(WeakHashMap<ExtractorLink, Interceptor>())
    private fun result(item: RMCItem): SearchResponse = newTvSeriesSearchResponse(item.title, item.endpoint, TvType.TvSeries) { posterUrl = item.poster }
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse = withContext(Dispatchers.IO) {
        val shelves = client.home(request.data, page)
        newHomePageResponse(shelves.map { (title, items) -> HomePageList("${request.name} · $title", items.map(::result), true) }, hasNext = shelves.isNotEmpty())
    }
    override suspend fun search(query: String): List<SearchResponse> = withContext(Dispatchers.IO) {
        if (query.isBlank()) emptyList() else client.search(query).map(::result)
    }
    override suspend fun load(url: String): LoadResponse = withContext(Dispatchers.IO) {
        val direct = url.toHttpUrl().queryParameter("page_type") == "player"
        val details = if (direct) client.details(url.toHttpUrl().newBuilder().setQueryParameter("page_type", "fip").build().toString()) else client.details(url)
        val item = details.item
        if (details.recommendations.isNotEmpty()) return@withContext newTvSeriesLoadResponse(item.title, url, TvType.TvSeries, emptyList()) {
            posterUrl = item.poster; recommendations = details.recommendations.map(::result)
        }
        if (details.episodes.isEmpty()) return@withContext newMovieLoadResponse(item.title, url, TvType.Movie, if (direct) url else item.endpoint) {
            posterUrl = item.poster; plot = item.description
        }
        newTvSeriesLoadResponse(item.title, url, TvType.TvSeries, details.episodes.mapIndexed { index, episodeItem ->
            newEpisode(episodeItem.endpoint, initializer = {
                name = episodeItem.title; posterUrl = episodeItem.poster; description = episodeItem.description
                season = episodeItem.season ?: 1; episode = episodeItem.episode ?: index + 1
            }, fix = false)
        }) { posterUrl = item.poster; plot = item.description }
    }
    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit): Boolean = withContext(Dispatchers.IO) {
        if (isCasting) throw ErrorLoadingException("RMC+ Chromecast playback is not supported yet.")
        val stream = client.playback(data, RMCRuntime.auth)
        val type = if (stream.dash) ExtractorLinkType.DASH else ExtractorLinkType.M3U8
        val link = if (stream.token != null) newDrmExtractorLink(name, "RMC+ · Widevine", stream.url, type, WIDEVINE_UUID) {
            licenseUrl = RMCClient.LICENSE
            keyRequestParameters = hashMapOf("x-dt-auth-token" to stream.token)
        }.also { interceptors[it] = RMCLicenseInterceptor(stream.token) }
        else newExtractorLink(name, name, stream.url, type)
        callback(link)
        true
    }
    override fun getVideoInterceptor(extractorLink: ExtractorLink): Interceptor? = interceptors[extractorLink]
}
