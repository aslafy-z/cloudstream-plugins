package fr.m6plus

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.lagradost.cloudstream3.ui.player.RepoLinkGenerator
import fr.m6plus.api.M6ApiClient
import fr.m6plus.api.M6AuthClient
import fr.m6plus.api.M6CatalogClient
import fr.m6plus.api.M6PlaybackClient
import fr.m6plus.api.M6SearchClient
import fr.m6plus.auth.M6SessionManager
import fr.m6plus.settings.M6SettingsStore
import fr.m6plus.cast.M6CastManager
import fr.m6plus.cast.M6NativeCastClient
import fr.m6plus.cast.M6NativeCastManager
import fr.m6plus.diagnostics.M6Diagnostics

internal object M6Runtime {
    private val http = M6ApiClient()
    lateinit var sessions: M6SessionManager
        private set
    val catalog = M6CatalogClient(http)
    val search = M6SearchClient(http)
    lateinit var playback: M6PlaybackClient
        private set
    private lateinit var applicationContext: Context
    private val castManager = lazy { M6CastManager(applicationContext, playback) }
    val cast: M6CastManager get() = castManager.value
    val castConnected: Boolean get() = castManager.isInitialized() && castManager.value.connected
    val nativeClient get() = M6NativeCastClient(http, sessions)
    private val nativeManager = lazy { M6NativeCastManager() }
    val nativeCast get() = nativeManager.value

    fun castMessage(message: String) {
        Handler(Looper.getMainLooper()).post {
            android.widget.Toast.makeText(applicationContext, message, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    fun clearPlaybackCache() {
        synchronized(RepoLinkGenerator.cache) { RepoLinkGenerator.cache.keys.removeAll { it.first == "M6+" } }
    }

    /** Standard Cast reuses cached links for 20 minutes, which can outlive an evicted relay session. */
    fun forgetPlaybackLink(url: String) {
        synchronized(RepoLinkGenerator.cache) {
            RepoLinkGenerator.cache.entries.removeAll { (key, cache) ->
                key.first == "M6+" && synchronized(cache) { cache.linkCache.any { it.url == url } }
            }
        }
    }

    fun stopCast() {
        clearPlaybackCache()
        if (castManager.isInitialized()) castManager.value.stop()
        if (nativeManager.isInitialized()) nativeManager.value.stop()
    }

    fun close() {
        M6Diagnostics.record(M6Diagnostics.Event.PLUGIN_CLOSED)
        if (castManager.isInitialized()) castManager.value.close()
        if (nativeManager.isInitialized()) Handler(Looper.getMainLooper()).post { nativeManager.value.stop() }
    }

    fun init(context: Context) {
        M6Diagnostics.record(M6Diagnostics.Event.PLUGIN_LOADED)
        sessions = M6SessionManager(M6AuthClient(http), M6SettingsStore(context))
        playback = M6PlaybackClient(http, sessions)
        applicationContext = context.applicationContext
        Handler(Looper.getMainLooper()).post {
            // Cast is optional on host builds without Google Play services.
            runCatching { cast.observe() }
        }
    }
}
