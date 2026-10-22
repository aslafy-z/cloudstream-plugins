package fr.tf1plus

import android.content.Context
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

    fun init(context: Context) {
        TF1Diagnostics.record(TF1Diagnostics.Event.PLUGIN_LOADED)
        auth = TF1Auth(http, TF1EncryptedStore(context.applicationContext))
        playback = TF1Playback(http, auth)
    }

    fun clearPlaybackCache() {
        synchronized(RepoLinkGenerator.cache) { RepoLinkGenerator.cache.keys.removeAll { it.first == "TF1+" } }
    }

    fun close() {
        TF1Diagnostics.record(TF1Diagnostics.Event.PLUGIN_CLOSED)
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
        openSettings = { settingsContext ->
            (settingsContext as? AppCompatActivity)?.let { activity ->
                if (activity.supportFragmentManager.findFragmentByTag("tf1plus_settings") == null) {
                    TF1SettingsFragment().show(activity.supportFragmentManager, "tf1plus_settings")
                }
            }
        }
    }
}
