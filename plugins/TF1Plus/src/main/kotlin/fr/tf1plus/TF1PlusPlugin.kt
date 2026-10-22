package fr.tf1plus

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.ui.player.RepoLinkGenerator

internal object TF1Runtime {
    private val http = TF1Http()
    val catalog = TF1Catalog(http)
    lateinit var auth: TF1Auth
        private set
    lateinit var playback: TF1Playback
        private set

    private lateinit var applicationContext: Context
    private val castManager = lazy { TF1CastManager(applicationContext) }
    val cast: TF1CastManager get() = castManager.value
    val castConnected: Boolean get() = castManager.isInitialized() && castManager.value.connected

    fun castMessage(message: String) {
        Handler(Looper.getMainLooper()).post {
            android.widget.Toast.makeText(applicationContext, message, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    fun stopCast() {
        clearPlaybackCache()
        if (castManager.isInitialized()) castManager.value.stop()
    }

    fun init(context: Context) {
        TF1Diagnostics.record(TF1Diagnostics.Event.PLUGIN_LOADED)
        auth = TF1Auth(http, TF1EncryptedStore(context.applicationContext))
        playback = TF1Playback(http, auth)
        applicationContext = context.applicationContext
        Handler(Looper.getMainLooper()).post {
            // Cast is optional on host builds without Google Play services.
            runCatching { cast.observe() }
        }
    }

    fun clearPlaybackCache() {
        synchronized(RepoLinkGenerator.cache) { RepoLinkGenerator.cache.keys.removeAll { it.first == "TF1+" } }
    }

    fun close() {
        TF1Diagnostics.record(TF1Diagnostics.Event.PLUGIN_CLOSED)
        if (castManager.isInitialized()) castManager.value.close()
        if (::auth.isInitialized) auth.clearMemory()
    }
}

@CloudstreamPlugin
class TF1PlusPlugin : Plugin() {
    override fun beforeUnload() {
        TF1Runtime.close()
    }
    override fun load(context: Context) {
        TF1Runtime.init(context)
        registerMainAPI(TF1PlusProvider())
        registerVideoClickAction(TF1DirectCastAction())
        openSettings = { settingsContext ->
            (settingsContext as? AppCompatActivity)?.let { activity ->
                if (activity.supportFragmentManager.findFragmentByTag("tf1plus_settings") == null) {
                    TF1SettingsFragment().show(activity.supportFragmentManager, "tf1plus_settings")
                }
            }
        }
    }
}
