package fr.rmcplus

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManager
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.CastMediaControlIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/** The relay rewrites DASH Widevine only; unprotected replays go to the TV unchanged. */
internal object RMCCastRoute {
    const val UNSUPPORTED = "This RMC+ replay uses Widevine HLS, which Chromecast cannot play through the relay. Disconnect Chromecast to watch it on this device."
    fun supported(stream: RMCStream) = stream.token == null || stream.dash
    fun relayed(stream: RMCStream): Boolean {
        require(supported(stream)) { UNSUPPORTED }
        return stream.token != null
    }
}

internal class RMCCastManager(private val context: Context) {
    private val http = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()
    private var relay: RMCCastRelay? = null
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
        if (connected) RMCDiagnostics.record(RMCDiagnostics.Cast.CONNECTED)
    }

    suspend fun link(stream: RMCStream): String {
        require(RMCCastRoute.relayed(stream)) { "The RMC+ replay is not protected." }
        val token = stream.token!!
        RMCDiagnostics.record(RMCDiagnostics.Cast.SESSION)
        val address = withContext(Dispatchers.Main) {
            observe()
            val manager = CastContext.getSharedInstance(context).sessionManager
            check(manager.currentCastSession?.applicationMetadata?.applicationId == CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID) {
                "Connect CloudStream to its standard Chromecast receiver first."
            }
            @Suppress("DEPRECATION")
            val target = manager.currentCastSession?.castDevice?.ipAddress
                ?: throw IllegalStateException("Connect CloudStream to your Chromecast first.")
            target
        }
        val expected = synchronized(this) { generation }
        return withContext(Dispatchers.IO) {
            RMCDiagnostics.record(RMCDiagnostics.Cast.NETWORK)
            val local = DatagramSocket().use { socket -> socket.connect(address, 8009); socket.localAddress }
            RMCDiagnostics.record(RMCDiagnostics.Cast.FETCH)
            val (xml, origin) = http.newCall(Request.Builder().url(stream.url).build()).execute().use { response ->
                check(response.isSuccessful) { "The RMC+ manifest is unavailable." }
                val body = response.body ?: throw IllegalStateException("The RMC+ manifest is empty.")
                // Manifests are small; refuse unexpectedly large responses before allocating their contents.
                val source = body.source()
                check(!source.request(1_048_577)) { "The RMC+ manifest is too large." }
                source.readUtf8() to response.request.url.toString()
            }
            synchronized(this@RMCCastManager) {
                check(generation == expected && connected) { "The Chromecast session has ended. Reconnect to Cast." }
                if (peer != address || relay?.isClosed != false) {
                    relay?.close()
                    relay = RMCCastRelay(local, address, onEvent = RMCDiagnostics::record,
                        onEvict = RMCRuntime::forgetPlaybackLink)
                    peer = address
                }
                RMCDiagnostics.record(RMCDiagnostics.Cast.REWRITE)
                relay!!.add(xml, origin, RMCCastLicense(token)::request).also {
                    RMCDiagnostics.record(RMCDiagnostics.Cast.READY)
                }
            }
        }
    }

    fun stop() {
        if (relay != null) RMCDiagnostics.record(RMCDiagnostics.Cast.STOPPED)
        synchronized(this) { generation++; relay?.close(); relay = null; peer = null }
        // Only this provider's entries are invalidated; other plugins keep their cache.
        RMCRuntime.clearPlaybackCache()
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
