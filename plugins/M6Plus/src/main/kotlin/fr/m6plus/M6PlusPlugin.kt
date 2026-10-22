package fr.m6plus

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import fr.m6plus.settings.M6SettingsFragment
import fr.m6plus.cast.M6NativeCastAction
import fr.m6plus.cast.M6DirectCastAction

@CloudstreamPlugin
class M6PlusPlugin : Plugin() {
    override fun beforeUnload() { M6Runtime.close() }
    override fun load(context: Context) {
        M6Runtime.init(context.applicationContext)
        registerMainAPI(M6PlusProvider())
        registerVideoClickAction(M6NativeCastAction())
        registerVideoClickAction(M6DirectCastAction())
        openSettings = { settingsContext ->
            (settingsContext as? AppCompatActivity)?.let { activity ->
                if (activity.supportFragmentManager.findFragmentByTag("m6plus_settings") == null) {
                    M6SettingsFragment().show(activity.supportFragmentManager, "m6plus_settings")
                }
            }
        }
    }
}
