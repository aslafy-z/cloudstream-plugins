package fr.tv5mondeplus

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
class TV5DirectCastAction : VideoClickAction() {
    override val name = txt("TV5MONDEplus direct Chromecast (relay)")
    override fun shouldShow(context: Context?, video: ResultEpisode?): Boolean = context != null && video?.apiName == TV5Runtime.NAME

    override suspend fun runAction(context: Context?, video: ResultEpisode, result: LinkLoadingResult, index: Int?) {
        val activity = context ?: throw ErrorLoadingException("Open the programme details to start Chromecast.")
        val session = withContext(Dispatchers.Main) {
            TV5Runtime.cast.observe()
            CastContext.getSharedInstance(activity).sessionManager.currentCastSession?.takeIf {
                it.applicationMetadata?.applicationId == CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID
            } ?: throw ErrorLoadingException("Stop native Cast, then connect CloudStream to the TV using its Chromecast button.")
        }
        try {
            val stream = withContext(Dispatchers.IO) { TV5Runtime.client.playback(video.data) }
            val url = TV5Runtime.cast.link(stream)
            withContext(Dispatchers.Main) {
                check(CastContext.getSharedInstance(activity).sessionManager.currentCastSession === session) {
                    "The Chromecast session has changed."
                }
                val client = checkNotNull(session.remoteMediaClient)
                val request = TV5DirectCastRequest.build(url, video.name ?: "TV5MONDEplus", video.position, emptyList())
                client.load(request).setResultCallback { response ->
                    if (!response.status.isSuccess) {
                        TV5Runtime.castMessage("The TV rejected playback (code ${response.status.statusCode}).")
                    }
                }
            }
        } catch (error: CancellationException) { throw error }
        catch (error: ErrorLoadingException) { throw error }
        catch (_: Exception) {
            throw ErrorLoadingException("The TV5MONDEplus Chromecast relay failed. Keep the phone on the TV's network and retry.")
        }
    }
}
