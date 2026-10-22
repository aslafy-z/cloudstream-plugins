package fr.m6plus.cast

import fr.m6plus.diagnostics.M6Diagnostics
import android.content.Context
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastContext
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.actions.VideoClickAction
import com.lagradost.cloudstream3.ui.result.LinkLoadingResult
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.utils.txt
import fr.m6plus.M6Runtime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Sends LOAD explicitly, independently of the host's cached links and Cast launch helper. */
class M6DirectCastAction : VideoClickAction() {
    override val name = txt("M6+ direct Chromecast (relay)")
    override fun shouldShow(context: Context?, video: ResultEpisode?): Boolean = context != null && video?.apiName == "M6+"

    override suspend fun runAction(context: Context?, video: ResultEpisode, result: LinkLoadingResult, index: Int?) {
        val activity = context ?: throw ErrorLoadingException("Open the replay details to start Chromecast.")
        M6Diagnostics.record(M6Diagnostics.Event.SESSION)
        val session = withContext(Dispatchers.Main) {
            M6Runtime.cast.observe()
            CastContext.getSharedInstance(activity).sessionManager.currentCastSession?.takeIf {
                it.applicationMetadata?.applicationId == CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID
            } ?: throw ErrorLoadingException("Stop native Cast, then connect CloudStream to the TV using its Chromecast button.")
        }
        try {
            M6Diagnostics.record(M6Diagnostics.Event.PREPARING)
            val playback = withContext(Dispatchers.IO) { M6Runtime.playback.resolve(video.data) }
            val url = M6Runtime.cast.link(video.data, playback.manifest)
            withContext(Dispatchers.Main) {
                check(CastContext.getSharedInstance(activity).sessionManager.currentCastSession === session) {
                    "The Chromecast session has changed."
                }
                val client = checkNotNull(session.remoteMediaClient)
                val request = M6DirectCastRequest.build(url, video.name ?: "M6+ replay", video.position, playback.subtitles)
                M6Diagnostics.record(M6Diagnostics.Event.LOAD_SENT)
                client.load(request).setResultCallback { response ->
                    if (response.status.isSuccess) M6Diagnostics.record(M6Diagnostics.Event.LOAD_ACCEPTED)
                    else {
                        M6Diagnostics.rejected(response.status.statusCode)
                        M6Runtime.castMessage("The TV rejected playback (code ${response.status.statusCode}). Check Plugin diagnostics in the M6+ settings.")
                    }
                }
            }
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) {
            val stage = M6Diagnostics.failed()
            throw ErrorLoadingException("The Chromecast relay failed ($stage). Check Plugin diagnostics in the M6+ settings.")
        }
    }
}
