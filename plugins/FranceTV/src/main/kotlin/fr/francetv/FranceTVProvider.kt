package fr.francetv

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class FranceTVProvider : MainAPI() {
    override var name = "france.tv"
    override var mainUrl = "https://www.france.tv"
    override var lang = "fr"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override val hasMainPage = true
    override val hasChromecastSupport = false
    override val hasDownloadSupport = false
    override val mainPage = mainPageOf("france-2" to "France 2", "france-3" to "France 3", "france-4" to "France 4",
        "france-5" to "France 5", "franceinfo" to "franceinfo", "slash" to "France tv Slash")
    private val client = FranceTVClient()
    private fun result(item: FTVItem): SearchResponse {
        val url = "$mainUrl/${if (item.program) "programmes" else "video"}/${item.id}"
        return if (item.program) newTvSeriesSearchResponse(item.title, url, TvType.TvSeries) { posterUrl = item.poster }
        else newMovieSearchResponse(item.title, url, TvType.Movie) { posterUrl = item.poster }
    }
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse = withContext(Dispatchers.IO) {
        newHomePageResponse(client.home(request.data).map { (title, items) -> HomePageList("${request.name} · $title", items.map(::result), true) }, hasNext = false)
    }
    override suspend fun search(query: String): List<SearchResponse> = withContext(Dispatchers.IO) {
        if (query.isBlank()) emptyList() else client.search(query).map(::result)
    }
    override suspend fun load(url: String): LoadResponse = withContext(Dispatchers.IO) {
        val id = url.substringAfterLast('/')
        if ("/video/" in url) {
            val meta = client.video(id).optJSONObject("meta") ?: JSONObject()
            return@withContext newMovieLoadResponse(meta.text("additional_title") ?: meta.text("title") ?: "france.tv replay", url, TvType.Movie, id) {
                plot = meta.text("description"); posterUrl = meta.text("image_url")
            }
        }
        val details = client.details(id)
        newTvSeriesLoadResponse(details.item.title, url, TvType.TvSeries, details.episodes.mapIndexed { index, item ->
            newEpisode(item.id, initializer = {
                name = item.title; posterUrl = item.poster; description = item.description
                season = item.season ?: 1; episode = item.episode ?: index + 1
            }, fix = false)
        }) { posterUrl = details.item.poster; plot = details.item.description }
    }
    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit): Boolean = withContext(Dispatchers.IO) {
        val stream = client.playback(data)
        if (stream.license != null) {
            if (isCasting) throw ErrorLoadingException("france.tv Widevine Chromecast playback is not supported yet.")
            callback(newDrmExtractorLink(name, "france.tv · Widevine", stream.url, ExtractorLinkType.DASH, WIDEVINE_UUID) {
                licenseUrl = stream.license
                keyRequestParameters = HashMap(stream.headers)
            })
        } else callback(newExtractorLink(name, name, stream.url, if (stream.dash) ExtractorLinkType.DASH else ExtractorLinkType.M3U8) {
            headers = mapOf("User-Agent" to FranceTVClient.USER_AGENT)
        })
        true
    }
}
