package fr.tv5mondeplus

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.ui.player.RepoLinkGenerator

internal object TV5Runtime {
    const val NAME = "TV5MONDEplus"
    val client = TV5Client()
    private lateinit var applicationContext: Context
    private val castManager = lazy { TV5CastManager(applicationContext) }
    val cast: TV5CastManager get() = castManager.value
    val castConnected: Boolean get() = castManager.isInitialized() && castManager.value.connected

    fun castMessage(message: String) {
        Handler(Looper.getMainLooper()).post { Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show() }
    }

    fun init(context: Context) {
        applicationContext = context.applicationContext
        Handler(Looper.getMainLooper()).post {
            // Cast is optional on host builds without Google Play services.
            runCatching { cast.observe() }
        }
    }

    fun clearPlaybackCache() {
        synchronized(RepoLinkGenerator.cache) { RepoLinkGenerator.cache.keys.removeAll { it.first == NAME } }
    }

    /** Standard Cast reuses cached links for 20 minutes, which can outlive an evicted relay session. */
    fun forgetPlaybackLink(url: String) {
        synchronized(RepoLinkGenerator.cache) {
            RepoLinkGenerator.cache.entries.removeAll { (key, cache) ->
                key.first == NAME && synchronized(cache) { cache.linkCache.any { it.url == url } }
            }
        }
    }

    fun close() {
        if (castManager.isInitialized()) castManager.value.close()
    }
}

@CloudstreamPlugin
class TV5Plugin : Plugin() {
    override fun beforeUnload() { TV5Runtime.close() }
    override fun load(context: Context) {
        TV5Runtime.init(context)
        registerMainAPI(TV5Provider())
        registerVideoClickAction(TV5DirectCastAction())
    }
}
