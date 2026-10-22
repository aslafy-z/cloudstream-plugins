package fr.m6plus

import android.content.Context
import fr.m6plus.api.M6ApiClient
import fr.m6plus.api.M6AuthClient
import fr.m6plus.api.M6CatalogClient
import fr.m6plus.api.M6PlaybackClient
import fr.m6plus.api.M6SearchClient
import fr.m6plus.auth.M6SessionManager
import fr.m6plus.settings.M6SettingsStore

internal object M6Runtime {
    private val http = M6ApiClient()
    lateinit var sessions: M6SessionManager
        private set
    val catalog = M6CatalogClient(http)
    val search = M6SearchClient(http)
    lateinit var playback: M6PlaybackClient
        private set

    fun init(context: Context) {
        sessions = M6SessionManager(M6AuthClient(http), M6SettingsStore(context))
        playback = M6PlaybackClient(http, sessions)
    }
}
