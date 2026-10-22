package fr.m6plus.cast

import android.content.Context
import fr.m6plus.diagnostics.M6Diagnostics
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.mediarouter.app.MediaRouteChooserDialog
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManager
import com.google.android.gms.cast.framework.SessionManagerListener
import org.json.JSONObject

/** Explicit native action: switches receiver only when selected, and restores the default afterwards. */
internal class M6NativeCastManager {
    private var castContext: CastContext? = null
    private var manager: SessionManager? = null
    private var active: CastSession? = null
    private var pending: JSONObject? = null
    private var position = 0.0
    private var chooser: MediaRouteChooserDialog? = null
    private var appContext: Context? = null
    private val main = Handler(Looper.getMainLooper())
    private val timeout = Runnable { if (pending != null) fail() }
    private val listener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) = load(session)
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) = load(session)
        override fun onSessionEnded(session: CastSession, error: Int) { if (active === session) stop() }
        override fun onSessionStartFailed(session: CastSession, error: Int) { if (pending != null) fail() }
        override fun onSessionResumeFailed(session: CastSession, error: Int) { if (active === session || pending != null) fail() }
        override fun onSessionStarting(session: CastSession) = Unit
        override fun onSessionEnding(session: CastSession) = Unit
        override fun onSessionResuming(session: CastSession, sessionId: String) = Unit
        override fun onSessionSuspended(session: CastSession, reason: Int) = Unit
    }

    fun start(context: Context, payload: JSONObject, startSeconds: Double) {
        M6Diagnostics.record(M6Diagnostics.Event.NATIVE_CONNECTING)
        check(pending == null) { "A Chromecast connection is already in progress." }
        appContext = context.applicationContext
        val cast = CastContext.getSharedInstance(context)
        castContext = cast
        if (manager == null) {
            manager = cast.sessionManager
            manager!!.addSessionManagerListener(listener, CastSession::class.java)
        }
        pending = payload
        position = startSeconds
        main.postDelayed(timeout, 60_000)
        val current = manager!!.currentCastSession
        if (current?.applicationMetadata?.applicationId == RECEIVER_ID) { load(current); return }
        // Changing receiver ends the old Cast session. Its end callback must not cancel the pending native launch.
        cast.setReceiverApplicationId(RECEIVER_ID)
        val selector = MediaRouteSelector.Builder().addControlCategory(CastMediaControlIntent.categoryForCast(RECEIVER_ID)).build()
        chooser = MediaRouteChooserDialog(context).apply {
            routeSelector = selector
            setOnDismissListener {
                chooser = null
                if (pending != null && !MediaRouter.getInstance(context).selectedRoute.matchesSelector(selector)) stop()
            }
            show()
        }
    }

    private fun load(session: CastSession) {
        if (session.applicationMetadata?.applicationId != RECEIVER_ID) return
        active = session
        val data = pending ?: return
        pending = null
        main.removeCallbacks(timeout)
        chooser?.dismiss()
        chooser = null
        val info = MediaInfo.Builder(data.getString("idVideo"))
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED).setContentType("application/dash+xml")
            .setCustomData(data).build()
        val client = session.remoteMediaClient ?: run { fail(); return }
        val request = com.google.android.gms.cast.MediaLoadRequestData.Builder().setMediaInfo(info)
            .setCurrentTime((position * 1000).toLong()).build()
        M6Diagnostics.record(M6Diagnostics.Event.NATIVE_SENT)
        client.load(request).setResultCallback { result ->
            if (result.status.isSuccess) M6Diagnostics.record(M6Diagnostics.Event.NATIVE_ACCEPTED)
            else fail()
        }
    }

    private fun fail() {
        M6Diagnostics.record(M6Diagnostics.Event.NATIVE_FAILED)
        appContext?.let { Toast.makeText(it, "The native M6+ Chromecast player could not start.", Toast.LENGTH_LONG).show() }
        stop()
    }

    fun stop() {
        if (pending != null || active != null) M6Diagnostics.record(M6Diagnostics.Event.NATIVE_STOPPED)
        pending = null
        main.removeCallbacks(timeout)
        chooser?.setOnDismissListener(null)
        chooser?.dismiss()
        chooser = null
        manager?.removeSessionManagerListener(listener, CastSession::class.java)
        manager = null
        active = null
        castContext?.setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
        castContext = null
        appContext = null
    }

    companion object { private const val RECEIVER_ID = "9CCF51A9" }
}
