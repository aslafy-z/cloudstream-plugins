package fr.tf1plus

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import java.util.Collections
import java.util.WeakHashMap

class TF1PlusProvider : MainAPI() {
    override var mainUrl = "https://www.tf1.fr"
    override var name = "TF1+"
    override var lang = "fr"
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)
    override val hasMainPage = true
    override val hasDownloadSupport = false
    override val hasChromecastSupport = false
    override val mainPage = mainPageOf(
        "tf1" to "TF1", "tmc" to "TMC", "tfx" to "TFX",
        "tf1-series-films" to "TF1 Séries Films", "lci" to "LCI",
        "all" to "Tous les programmes"
    )
    private val videoInterceptors =
        Collections.synchronizedMap(WeakHashMap<ExtractorLink, Interceptor>())

    private fun programmeResult(value: TF1Programme): SearchResponse =
        newTvSeriesSearchResponse(value.title,
            "$mainUrl/programmes/${value.slug}/${value.id}", TvType.TvSeries) {
            posterUrl = value.poster
        }

    private fun videoResult(value: TF1Video): SearchResponse =
        newMovieSearchResponse(value.title, "$mainUrl/video/${value.id}", TvType.Movie) {
            posterUrl = value.poster
        }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse =
        withContext(Dispatchers.IO) {
            val programmes = TF1Runtime.catalog.programmes(request.data, page.coerceAtLeast(1) - 1)
            newHomePageResponse(
                HomePageList(request.name, programmes.map(::programmeResult), true),
                hasNext = programmes.size >= 60
            )
        }

    override suspend fun search(query: String): List<SearchResponse> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val programmes = TF1Runtime.catalog.searchProgrammes(query)
        val videos = TF1Runtime.catalog.searchVideos(query)
        programmes.distinctBy { it.id }.map(::programmeResult) +
            videos.distinctBy { it.id }.map(::videoResult)
    }

    override suspend fun load(url: String): LoadResponse = withContext(Dispatchers.IO) {
        TF1Diagnostics.record(TF1Diagnostics.Event.EPISODE_SELECTED)
        val path = url.substringBefore('?').trimEnd('/')
        val id = path.substringAfterLast('/')
        if ("/video/" in path) {
            return@withContext newMovieLoadResponse("TF1+ replay", url, TvType.Movie, id)
        }
        val programme = TF1Runtime.catalog.programme(id)
        val fallbackSlug = path.removeSuffix("/$id").substringAfterLast('/')
        val slug = programme?.slug?.takeIf { it.isNotBlank() } ?: fallbackSlug
        val episodes = TF1Runtime.catalog.videosWithSeasons(slug, id).map { video ->
            newEpisode(video.id, initializer = {
                name = video.title
                posterUrl = video.poster
                description = video.description
                season = video.season
                episode = video.number
            }, fix = false)
        }
        if (episodes.isEmpty()) {
            TF1Diagnostics.record(TF1Diagnostics.Event.NO_REPLAYS)
            throw ErrorLoadingException("Aucun replay disponible pour ce programme TF1+.")
        }
        newTvSeriesLoadResponse(programme?.title ?: slug.replace('-', ' '),
            url, TvType.TvSeries, episodes) {
            posterUrl = programme?.poster
            plot = programme?.description
        }
    }

    override suspend fun loadLinks(
        data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        TF1Diagnostics.record(TF1Diagnostics.Event.PLAYBACK_REQUESTED)
        val stream = try {
            TF1Diagnostics.trace(TF1Diagnostics.Operation.PLAYBACK) {
                TF1Runtime.playback.resolve(data)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            throw ErrorLoadingException("Lecture TF1+ indisponible. Voir Plugin diagnostics dans les paramètres.")
        }
        TF1Diagnostics.record(TF1Diagnostics.Event.MANIFEST_READY)
        val drm = newDrmExtractorLink(name, "TF1+ · Widevine", stream.manifest,
            ExtractorLinkType.DASH, WIDEVINE_UUID) {
            licenseUrl = stream.licenseUrl
        }
        // Install even without custom DRM headers so license requests remain observable.
        videoInterceptors[drm] = TF1LicenseInterceptor(stream.licenseUrl, stream.licenseHeaders)
        stream.subtitles.forEach { subtitleCallback(newSubtitleFile("Français", it)) }
        callback(drm)
        TF1Diagnostics.record(TF1Diagnostics.Event.LINK_EMITTED)
        true
    }

    override fun getVideoInterceptor(extractorLink: ExtractorLink): Interceptor? {
        TF1Diagnostics.record(TF1Diagnostics.Event.PLAYER_INITIALIZED)
        return videoInterceptors[extractorLink]
    }
}
