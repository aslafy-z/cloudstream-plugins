package fr.francetv

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.ui.player.RepoLinkGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

internal object FranceTVRuntime {
    const val API_NAME = "france.tv"
    val client = FranceTVClient()

    private lateinit var applicationContext: Context
    private val castManager = lazy { FranceTVCastManager(applicationContext) }
    val cast: FranceTVCastManager get() = castManager.value
    val castConnected: Boolean get() = castManager.isInitialized() && castManager.value.connected

    fun init(context: Context) {
        applicationContext = context.applicationContext
        Handler(Looper.getMainLooper()).post {
            // Cast is optional on host builds without Google Play services.
            runCatching { cast.observe() }
        }
    }

    suspend fun playback(id: String): FTVPlayback = withContext(Dispatchers.IO) {
        try { client.playback(id) }
        catch (error: IOException) { throw ErrorLoadingException(error.message ?: "france.tv playback is unavailable.") }
        catch (error: IllegalStateException) { throw ErrorLoadingException(error.message ?: "france.tv playback is unavailable.") }
    }

    /** Only messages written by this plugin are shown; they never contain tokens or stream URLs. */
    fun castFailure(error: Exception): String =
        "france.tv Chromecast failed: " + ((error as? IllegalStateException)?.message ?: "the relay could not prepare playback.")

    fun castMessage(message: String) {
        Handler(Looper.getMainLooper()).post {
            android.widget.Toast.makeText(applicationContext, message, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    fun clearPlaybackCache() {
        synchronized(RepoLinkGenerator.cache) { RepoLinkGenerator.cache.keys.removeAll { it.first == API_NAME } }
    }

    /** Standard Cast reuses cached links for 20 minutes, which can outlive an evicted relay session. */
    fun forgetPlaybackLink(url: String) {
        synchronized(RepoLinkGenerator.cache) {
            RepoLinkGenerator.cache.entries.removeAll { (key, cache) ->
                key.first == API_NAME && synchronized(cache) { cache.linkCache.any { it.url == url } }
            }
        }
    }

    fun close() {
        if (castManager.isInitialized()) castManager.value.close()
    }
}

@CloudstreamPlugin
class FranceTVPlugin : Plugin() {
    override fun beforeUnload() { FranceTVRuntime.close() }
    override fun load(context: Context) {
        FranceTVRuntime.init(context)
        registerMainAPI(FranceTVProvider())
        registerVideoClickAction(FranceTVDirectCastAction())
    }
}
