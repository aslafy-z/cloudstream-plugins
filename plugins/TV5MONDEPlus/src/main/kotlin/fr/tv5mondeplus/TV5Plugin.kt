package fr.tv5mondeplus

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class TV5Plugin : Plugin() {
    override fun load(context: Context) { registerMainAPI(TV5Provider()) }
}
