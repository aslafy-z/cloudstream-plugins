package fr.francetv

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManager
import com.google.android.gms.cast.framework.SessionManagerListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.TimeUnit

internal class FranceTVCastManager(private val context: Context) {
    private val http = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()
    private var relay: FranceTVCastRelay? = null
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
    }

    suspend fun link(stream: FTVPlayback): String {
        val license = checkNotNull(stream.license) { "This france.tv replay is not protected." }
        check(stream.dash) { "Encrypted HLS is not supported by this plugin." }
        val address = withContext(Dispatchers.Main) {
            observe()
            val manager = CastContext.getSharedInstance(context).sessionManager
            check(manager.currentCastSession?.applicationMetadata?.applicationId == CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID) {
                "Connect CloudStream to its standard Chromecast receiver first."
            }
            @Suppress("DEPRECATION")
            manager.currentCastSession?.castDevice?.ipAddress
                ?: throw IllegalStateException("Connect CloudStream to your Chromecast first.")
        }
        val expected = synchronized(this) { generation }
        return withContext(Dispatchers.IO) {
            val local = DatagramSocket().use { socket -> socket.connect(address, 8009); socket.localAddress }
            val request = Request.Builder().url(stream.url).header("User-Agent", FranceTVClient.USER_AGENT).build()
            val (xml, origin) = http.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "The france.tv manifest is unavailable (HTTP ${response.code})." }
                val body = response.body ?: throw IllegalStateException("The france.tv manifest is empty.")
                // Manifests are small; refuse unexpectedly large responses before allocating their contents.
                val source = body.source()
                check(!source.request(1_048_577)) { "The france.tv manifest is too large." }
                source.readUtf8() to response.request.url.toString()
            }
            synchronized(this@FranceTVCastManager) {
                check(generation == expected && connected) { "The Chromecast session has ended. Reconnect to Cast." }
                if (peer != address || relay?.isClosed != false) {
                    relay?.close()
                    relay = FranceTVCastRelay(local, address, onEvict = FranceTVRuntime::forgetPlaybackLink)
                    peer = address
                }
                relay!!.add(xml, origin, FranceTVCastLicense(license, stream.headers)::request)
            }
        }
    }

    fun stop() {
        synchronized(this) { generation++; relay?.close(); relay = null; peer = null }
        // Only this provider's entries are invalidated; other plugins keep their cache.
        FranceTVRuntime.clearPlaybackCache()
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
