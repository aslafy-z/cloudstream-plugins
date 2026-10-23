package fr.rmcplus

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.ui.player.RepoLinkGenerator

internal object RMCRuntime {
    val client = RMCClient()
    lateinit var auth: RMCAuth
    private lateinit var applicationContext: Context
    private val castManager = lazy { RMCCastManager(applicationContext) }
    val cast: RMCCastManager get() = castManager.value
    val castConnected: Boolean get() = castManager.isInitialized() && castManager.value.connected

    fun init(context: Context) {
        applicationContext = context.applicationContext
        auth = RMCAuth(client, RMCEncryptedStore(context))
        Handler(Looper.getMainLooper()).post {
            // Cast is optional on host builds without Google Play services.
            runCatching { cast.observe() }
        }
    }

    fun castMessage(message: String) {
        Handler(Looper.getMainLooper()).post {
            android.widget.Toast.makeText(applicationContext, message, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    /** Widevine HLS cannot carry the relay's license URL to the standard receiver. */
    fun requireCastable(stream: RMCStream) {
        if (RMCCastRoute.supported(stream)) return
        RMCDiagnostics.record(RMCDiagnostics.Cast.UNSUPPORTED)
        throw ErrorLoadingException(RMCCastRoute.UNSUPPORTED)
    }

    /** Unprotected streams need no headers, so the TV loads them as-is. */
    suspend fun castUrl(stream: RMCStream): String = if (RMCCastRoute.relayed(stream)) cast.link(stream)
        else stream.url.also { RMCDiagnostics.record(RMCDiagnostics.Cast.DIRECT) }

    fun stopCast() {
        clearPlaybackCache()
        if (castManager.isInitialized()) castManager.value.stop()
    }

    fun clearPlaybackCache() {
        synchronized(RepoLinkGenerator.cache) { RepoLinkGenerator.cache.keys.removeAll { it.first == "RMC+" } }
    }

    /** Standard Cast reuses cached links for 20 minutes, which can outlive an evicted relay session. */
    fun forgetPlaybackLink(url: String) {
        synchronized(RepoLinkGenerator.cache) {
            RepoLinkGenerator.cache.entries.removeAll { (key, cache) ->
                key.first == "RMC+" && synchronized(cache) { cache.linkCache.any { it.url == url } }
            }
        }
    }

    fun close() {
        if (castManager.isInitialized()) castManager.value.close()
        clearPlaybackCache()
    }
}

@CloudstreamPlugin
class RMCPlusPlugin : Plugin() {
    override fun load(context: Context) {
        RMCRuntime.init(context)
        registerMainAPI(RMCProvider())
        registerVideoClickAction(RMCDirectCastAction())
        openSettings = { owner ->
            (owner as? AppCompatActivity)?.let {
                if (it.supportFragmentManager.findFragmentByTag("rmc_settings") == null)
                    RMCSettingsFragment().show(it.supportFragmentManager, "rmc_settings")
            }
        }
    }
    override fun beforeUnload() { RMCRuntime.close() }
}
