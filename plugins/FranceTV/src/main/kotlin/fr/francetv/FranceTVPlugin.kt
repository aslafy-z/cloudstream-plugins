package fr.francetv

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class FranceTVPlugin : Plugin() {
    override fun load(context: Context) { registerMainAPI(FranceTVProvider()) }
}
