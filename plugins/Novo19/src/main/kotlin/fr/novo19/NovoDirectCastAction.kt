package fr.novo19

import android.content.Context
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastContext
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.actions.VideoClickAction
import com.lagradost.cloudstream3.ui.result.LinkLoadingResult
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.utils.txt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Sends LOAD explicitly, independently of the host's cached links and Cast launch helper. */
class NovoDirectCastAction : VideoClickAction() {
    override val name = txt("NOVO19 direct Chromecast (relay)")
    override fun shouldShow(context: Context?, video: ResultEpisode?): Boolean = context != null && video?.apiName == "NOVO19"

    override suspend fun runAction(context: Context?, video: ResultEpisode, result: LinkLoadingResult, index: Int?) {
        val activity = context ?: throw ErrorLoadingException("Open the replay details to start Chromecast.")
        val session = withContext(Dispatchers.Main) {
            NovoRuntime.cast.observe()
            CastContext.getSharedInstance(activity).sessionManager.currentCastSession?.takeIf {
                it.applicationMetadata?.applicationId == CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID
            } ?: throw ErrorLoadingException("Connect CloudStream to the TV using its Chromecast button first.")
        }
        try {
            val stream = withContext(Dispatchers.IO) { NovoRuntime.client.playback(video.data) }
            // Unprotected DASH needs no headers, so the TV can fetch it directly.
            val url = if (stream.license == null) stream.url else NovoRuntime.cast.link(stream)
            withContext(Dispatchers.Main) {
                check(CastContext.getSharedInstance(activity).sessionManager.currentCastSession === session) {
                    "The Chromecast session has changed."
                }
                val client = checkNotNull(session.remoteMediaClient)
                client.load(NovoDirectCastRequest.build(url, video.name ?: "NOVO19 replay", video.position)).setResultCallback { response ->
                    if (!response.status.isSuccess) NovoRuntime.castMessage("The TV rejected NOVO19 playback (code ${response.status.statusCode}).")
                }
            }
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) {
            throw ErrorLoadingException("The NOVO19 Chromecast relay failed: ${error.message ?: "unknown error"}")
        }
    }
}
