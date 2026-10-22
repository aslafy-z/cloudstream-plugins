package fr.rmcplus

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.ui.player.RepoLinkGenerator

internal object RMCRuntime {
    val client = RMCClient()
    lateinit var auth: RMCAuth
    fun clearPlaybackCache() {
        synchronized(RepoLinkGenerator.cache) { RepoLinkGenerator.cache.keys.removeAll { it.first == "RMC+" } }
    }
}

@CloudstreamPlugin
class RMCPlusPlugin : Plugin() {
    override fun load(context: Context) {
        RMCRuntime.auth = RMCAuth(RMCRuntime.client, RMCEncryptedStore(context))
        registerMainAPI(RMCProvider())
        openSettings = { owner ->
            (owner as? AppCompatActivity)?.let {
                if (it.supportFragmentManager.findFragmentByTag("rmc_settings") == null)
                    RMCSettingsFragment().show(it.supportFragmentManager, "rmc_settings")
            }
        }
    }
    override fun beforeUnload() { RMCRuntime.clearPlaybackCache() }
}
