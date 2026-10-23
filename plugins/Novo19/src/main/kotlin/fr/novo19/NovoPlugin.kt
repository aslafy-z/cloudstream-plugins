package fr.novo19

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.ui.player.RepoLinkGenerator

internal object NovoRuntime {
    val client = NovoClient()
    private lateinit var applicationContext: Context
    private val castManager = lazy { NovoCastManager(applicationContext) }
    val cast: NovoCastManager get() = castManager.value
    val castConnected: Boolean get() = castManager.isInitialized() && castManager.value.connected

    fun castMessage(message: String) {
        Handler(Looper.getMainLooper()).post {
            android.widget.Toast.makeText(applicationContext, message, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    fun init(context: Context) {
        applicationContext = context.applicationContext
        Handler(Looper.getMainLooper()).post {
            // Cast is optional on host builds without Google Play services.
            runCatching { cast.observe() }
        }
    }

    fun clearPlaybackCache() {
        synchronized(RepoLinkGenerator.cache) { RepoLinkGenerator.cache.keys.removeAll { it.first == "NOVO19" } }
    }

    /** Standard Cast reuses cached links for 20 minutes, which can outlive an evicted relay session. */
    fun forgetPlaybackLink(url: String) {
        synchronized(RepoLinkGenerator.cache) {
            RepoLinkGenerator.cache.entries.removeAll { (key, cache) ->
                key.first == "NOVO19" && synchronized(cache) { cache.linkCache.any { it.url == url } }
            }
        }
    }

    fun close() {
        if (castManager.isInitialized()) castManager.value.close()
    }
}

@CloudstreamPlugin
class NovoPlugin : Plugin() {
    override fun beforeUnload() { NovoRuntime.close() }
    override fun load(context: Context) {
        NovoRuntime.init(context)
        registerMainAPI(NovoProvider())
        registerVideoClickAction(NovoDirectCastAction())
    }
}
