package fr.tf1plus

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
class TF1DirectCastAction : VideoClickAction() {
    override val name = txt("TF1+ direct Chromecast (relay)")
    override fun shouldShow(context: Context?, video: ResultEpisode?): Boolean = context != null && video?.apiName == "TF1+"

    override suspend fun runAction(context: Context?, video: ResultEpisode, result: LinkLoadingResult, index: Int?) {
        val activity = context ?: throw ErrorLoadingException("Open the replay details to start Chromecast.")
        TF1Diagnostics.record(TF1Diagnostics.Event.SESSION)
        val session = withContext(Dispatchers.Main) {
            TF1Runtime.cast.observe()
            CastContext.getSharedInstance(activity).sessionManager.currentCastSession?.takeIf {
                it.applicationMetadata?.applicationId == CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID
            } ?: throw ErrorLoadingException("Stop native Cast, then connect CloudStream to the TV using its Chromecast button.")
        }
        try {
            TF1Diagnostics.record(TF1Diagnostics.Event.PREPARING)
            val playback = withContext(Dispatchers.IO) { TF1Runtime.playback.resolve(video.data) }
            val url = TF1Runtime.cast.link(playback)
            withContext(Dispatchers.Main) {
                check(CastContext.getSharedInstance(activity).sessionManager.currentCastSession === session) {
                    "The Chromecast session has changed."
                }
                val client = checkNotNull(session.remoteMediaClient)
                val request = TF1DirectCastRequest.build(url, video.name ?: "TF1+ replay", video.position, playback.subtitles)
                TF1Diagnostics.record(TF1Diagnostics.Event.LOAD_SENT)
                client.load(request).setResultCallback { response ->
                    if (response.status.isSuccess) TF1Diagnostics.record(TF1Diagnostics.Event.LOAD_ACCEPTED)
                    else {
                        TF1Diagnostics.rejected(response.status.statusCode)
                        TF1Runtime.castMessage("The TV rejected playback (code ${response.status.statusCode}). Check Plugin diagnostics in the TF1+ settings.")
                    }
                }
            }
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) {
            val stage = TF1Diagnostics.failed()
            throw ErrorLoadingException("The Chromecast relay failed ($stage). Check Plugin diagnostics in the TF1+ settings.")
        }
    }
}
