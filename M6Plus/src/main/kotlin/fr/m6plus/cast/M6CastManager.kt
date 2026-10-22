package fr.m6plus.cast

import fr.m6plus.diagnostics.M6Diagnostics
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManager
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.CastMediaControlIntent
import com.lagradost.cloudstream3.ui.player.RepoLinkGenerator
import fr.m6plus.api.M6PlaybackClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.TimeUnit

internal class M6CastManager(private val context: Context, private val playback: M6PlaybackClient) {
    private val http = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()
    private var relay: M6CastRelay? = null
    private var peer: InetAddress? = null
    private var sessionManager: SessionManager? = null
    @Volatile var connected: Boolean = false
        private set
    private var generation = 0L
    private val listener = object : SessionManagerListener<CastSession> {
        override fun onSessionEnded(session: CastSession, error: Int) = changed(null)
        override fun onSessionStartFailed(session: CastSession, error: Int) = changed(null)
        override fun onSessionResumeFailed(session: CastSession, error: Int) = changed(null)
        override fun onSessionStarting(session: CastSession) = Unit
        override fun onSessionStarted(session: CastSession, sessionId: String) = changed(session)
        override fun onSessionEnding(session: CastSession) = Unit
        override fun onSessionResuming(session: CastSession, sessionId: String) = Unit
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) = changed(session)
        override fun onSessionSuspended(session: CastSession, reason: Int) = Unit
    }

    /** Called on the main thread. CloudStream caches links without including the isCasting flag. */
    fun observe() {
        if (sessionManager != null) return
        val manager = CastContext.getSharedInstance(context).sessionManager
        manager.addSessionManagerListener(listener, CastSession::class.java)
        sessionManager = manager
        changed(manager.currentCastSession)
    }

    private fun changed(session: CastSession?) {
        connected = session?.applicationMetadata?.applicationId == CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID
        stop()
        if (connected) M6Diagnostics.record(M6Diagnostics.Event.CONNECTED)
    }

    private fun clearLinks() {
        // Only this provider's entries are invalidated; other plugins keep their cache.
        synchronized(RepoLinkGenerator.cache) { RepoLinkGenerator.cache.keys.removeAll { it.first == "M6+" } }
    }

    suspend fun link(videoId: String, manifestUrl: String): String {
        M6Diagnostics.record(M6Diagnostics.Event.SESSION)
        val address = withContext(Dispatchers.Main) {
            observe()
            val manager = CastContext.getSharedInstance(context).sessionManager
            check(manager.currentCastSession?.applicationMetadata?.applicationId == CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID) {
                "Stop the native M6+ session, then reconnect CloudStream to Chromecast."
            }
            @Suppress("DEPRECATION")
            val target = manager.currentCastSession?.castDevice?.ipAddress
                ?: throw IllegalStateException("Connect CloudStream to your Chromecast first.")
            target
        }
        val expected = synchronized(this) { generation }
        return withContext(Dispatchers.IO) {
            M6Diagnostics.record(M6Diagnostics.Event.NETWORK)
            val local = DatagramSocket().use { socket -> socket.connect(address, 8009); socket.localAddress }
            M6Diagnostics.record(M6Diagnostics.Event.FETCH)
            val (xml, origin) = http.newCall(Request.Builder().url(manifestUrl).build()).execute().use { response ->
                check(response.isSuccessful) { "The M6+ manifest is unavailable." }
                val body = response.body ?: throw IllegalStateException("Le manifeste M6+ est vide.")
                // Manifests are small; refuse unexpectedly large responses before allocating their contents.
                val source = body.source()
                check(!source.request(1_048_577)) { "Le manifeste M6+ est trop volumineux." }
                source.readUtf8() to response.request.url.toString()
            }
            synchronized(this@M6CastManager) {
                check(generation == expected && connected) { "The Chromecast session has ended. Reconnect to Cast." }
                if (peer != address || relay?.isClosed != false) {
                    relay?.close()
                    relay = M6CastRelay(local, address, onEvent = M6Diagnostics::record)
                    peer = address
                }
                M6Diagnostics.record(M6Diagnostics.Event.REWRITE)
                relay!!.add(xml, origin) { challenge -> playback.license(videoId, challenge) }.also {
                    M6Diagnostics.record(M6Diagnostics.Event.READY)
                }
            }
        }
    }

    fun stop() {
        if (relay != null) M6Diagnostics.record(M6Diagnostics.Event.STOPPED)
        synchronized(this) { generation++; relay?.close(); relay = null; peer = null }
        clearLinks()
    }

    fun close() {
        stop()
        connected = false
        // Cast SDK access is restricted to the main thread.
        Handler(Looper.getMainLooper()).post {
            sessionManager?.removeSessionManagerListener(listener, CastSession::class.java)
            sessionManager = null
        }
    }
}
