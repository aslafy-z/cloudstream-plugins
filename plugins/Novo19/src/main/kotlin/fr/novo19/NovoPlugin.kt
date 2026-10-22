package fr.novo19

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class NovoPlugin : Plugin() {
    override fun load(context: Context) { registerMainAPI(NovoProvider()) }
}
