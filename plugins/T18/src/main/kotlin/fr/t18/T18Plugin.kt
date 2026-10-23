package fr.t18

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class T18Plugin : Plugin() {
    override fun load(context: Context) { registerMainAPI(T18Provider()); registerVideoClickAction(T18DirectCastAction()) }
}
