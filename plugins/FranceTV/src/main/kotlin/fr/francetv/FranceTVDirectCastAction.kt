package fr.francetv

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
class FranceTVDirectCastAction : VideoClickAction() {
    override val name = txt("france.tv direct Chromecast (relay)")
    override fun shouldShow(context: Context?, video: ResultEpisode?): Boolean = context != null && video?.apiName == "france.tv"

    override suspend fun runAction(context: Context?, video: ResultEpisode, result: LinkLoadingResult, index: Int?) {
        val activity = context ?: throw ErrorLoadingException("Open the replay details to start Chromecast.")
        val session = withContext(Dispatchers.Main) {
            FranceTVRuntime.cast.observe()
            CastContext.getSharedInstance(activity).sessionManager.currentCastSession?.takeIf {
                it.applicationMetadata?.applicationId == CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID
            } ?: throw ErrorLoadingException("Stop native Cast, then connect CloudStream to the TV using its Chromecast button.")
        }
        val playback = FranceTVRuntime.playback(video.data)
        try {
            // Unprotected replays use tokenized CDN URLs and go to the TV directly; only Widevine needs the relay.
            val url = if (playback.license == null) playback.url else FranceTVRuntime.cast.link(playback)
            withContext(Dispatchers.Main) {
                check(CastContext.getSharedInstance(activity).sessionManager.currentCastSession === session) {
                    "The Chromecast session has changed."
                }
                val client = checkNotNull(session.remoteMediaClient) { "The Chromecast session is not ready." }
                val request = FranceTVDirectCastRequest.build(url, playback.dash, video.name ?: "france.tv replay", video.position)
                client.load(request).setResultCallback { response ->
                    if (!response.status.isSuccess) FranceTVRuntime.castMessage(
                        "The TV rejected france.tv playback (code ${response.status.statusCode}).")
                }
            }
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) { throw ErrorLoadingException(FranceTVRuntime.castFailure(error)) }
    }
}
