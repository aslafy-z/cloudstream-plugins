package fr.m6plus

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import fr.m6plus.api.M6LicenseInterceptor
import fr.m6plus.api.M6PlaybackClient
import fr.m6plus.model.M6Program
import fr.m6plus.diagnostics.M6Diagnostics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import java.util.Collections
import java.util.WeakHashMap

class M6PlusProvider : MainAPI() {
    override var mainUrl = "https://www.m6.fr"
    override var name = "M6+"
    override var lang = "fr"
    override val supportedTypes = setOf(TvType.TvSeries)
    override val hasMainPage = true
    override val hasDownloadSupport = false
    override val hasChromecastSupport = true
    override val mainPage = mainPageOf("m6" to "M6", "w9" to "W9", "6ter" to "6ter", "gulli" to "Gulli")
    private val interceptors = Collections.synchronizedMap(WeakHashMap<ExtractorLink, Interceptor>())

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse = withContext(Dispatchers.IO) {
        val categories = M6Runtime.catalog.categories(request.data)
        val selected = categories.drop((page.coerceAtLeast(1) - 1) * 4).take(4)
        val rows = selected.map { (id, title) ->
            HomePageList("${request.name} · $title", M6Runtime.catalog.programs(id).map(::searchResult), true)
        }
        newHomePageResponse(rows, hasNext = page * 4 < categories.size)
    }

    override suspend fun search(query: String): List<SearchResponse> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val programs = try { M6Runtime.search.search(query) } catch (_: java.io.IOException) { fallbackSearch(query) }
            catch (_: fr.m6plus.api.M6ApiException) { fallbackSearch(query) }
            catch (_: org.json.JSONException) { fallbackSearch(query) }
        programs.map(::searchResult)
    }

    private fun fallbackSearch(query: String): List<M6Program> = M6Diagnostics.trace(M6Diagnostics.Operation.SEARCH_FALLBACK) {
        listOf("m6", "w9", "6ter", "gulli")
            .flatMap { M6Runtime.catalog.categories(it) }
            .distinctBy { it.first }
            .flatMap { M6Runtime.catalog.programs(it.first) }
            .distinctBy { it.id }
            .filter { it.title.contains(query, ignoreCase = true) }
    }

    private fun searchResult(program: M6Program): SearchResponse =
        newTvSeriesSearchResponse(program.title, "$mainUrl/programmes/${program.id}", TvType.TvSeries) {
            posterUrl = program.poster
        }

    override suspend fun load(url: String): LoadResponse = withContext(Dispatchers.IO) {
        val id = url.substringBefore('?').trimEnd('/').substringAfterLast('/')
        val (program, subcats) = M6Runtime.catalog.program(id)
        val episodes = M6Runtime.catalog.episodes(id, subcats).map { item ->
            newEpisode(item.id, initializer = {
                name = item.title
                posterUrl = item.poster
                description = item.description
                season = item.season
                episode = item.displayNumber
            }, fix = false)
        }
        if (episodes.isEmpty()) throw ErrorLoadingException("No replays are available for this programme.")
        newTvSeriesLoadResponse(program.title, url, TvType.TvSeries, episodes) {
            posterUrl = program.poster
            plot = program.description
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit): Boolean = withContext(Dispatchers.IO) {
        val casting = isCasting || M6Runtime.castConnected
        if (casting) M6Diagnostics.record(M6Diagnostics.Event.PREPARING)
        val playback = try { M6Runtime.playback.resolve(data) }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (casting) castFailed()
                throw ErrorLoadingException(error.message ?: "Unable to load the M6+ replay.")
            }
        if (casting) {
            val url = try { M6Runtime.cast.link(data, playback.manifest) }
                catch (error: CancellationException) { throw error }
                catch (_: Exception) { throw ErrorLoadingException(castFailed()) }
            playback.subtitles.forEach { subtitleCallback(newSubtitleFile("French", it)) }
            // The same cached link works in the phone player and on Cast while this session is active.
            callback(newDrmExtractorLink(name, "M6+ · Direct Chromecast", url, ExtractorLinkType.DASH, WIDEVINE_UUID) {
                licenseUrl = url.substringBeforeLast('/') + "/license"
            })
            return@withContext true
        }
        val link = newDrmExtractorLink(name, "M6+ · Widevine", playback.manifest,
            ExtractorLinkType.DASH, WIDEVINE_UUID) {
            licenseUrl = M6PlaybackClient.LICENSE_URL
            // keyRequestParameters configures the CDM, not HTTP request headers.
            keyRequestParameters = hashMapOf()
        }
        interceptors[link] = M6LicenseInterceptor(playback.token)
        playback.subtitles.forEach { subtitleCallback(newSubtitleFile("French", it)) }
        callback(link)
        true
    }

    private fun castFailed(): String {
        val stage = M6Diagnostics.failed()
        val message = "Chromecast relay failed ($stage). Check Plugin diagnostics in the M6+ settings."
        M6Runtime.castMessage(message)
        return message
    }

    override fun getVideoInterceptor(extractorLink: ExtractorLink): Interceptor? = interceptors[extractorLink]
}
