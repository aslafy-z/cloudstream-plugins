package fr.m6plus

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import fr.m6plus.settings.M6SettingsFragment

@CloudstreamPlugin
class M6PlusPlugin : Plugin() {
    override fun load(context: Context) {
        M6Runtime.init(context.applicationContext)
        registerMainAPI(M6PlusProvider())
        openSettings = {
            (context as? AppCompatActivity)?.let { activity ->
                M6SettingsFragment().show(activity.supportFragmentManager, "m6plus_settings")
            }
        }
    }
}
