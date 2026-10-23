package fr.t18

import android.content.Context
import android.widget.Toast
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

/** Sends LOAD explicitly, with the HLS segment format the host's Cast helper leaves out. */
class T18DirectCastAction : VideoClickAction() {
    override val name = txt("T18 direct Chromecast")
    override fun shouldShow(context: Context?, video: ResultEpisode?): Boolean = context != null && video?.apiName == "T18"
    private val client = T18Client()

    override suspend fun runAction(context: Context?, video: ResultEpisode, result: LinkLoadingResult, index: Int?) {
        val activity = context ?: throw ErrorLoadingException("Open the replay details to start Chromecast.")
        val session = withContext(Dispatchers.Main) {
            runCatching { CastContext.getSharedInstance(activity).sessionManager.currentCastSession }.getOrNull()?.takeIf {
                it.applicationMetadata?.applicationId == CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID
            } ?: throw ErrorLoadingException("Connect CloudStream to the TV using its Chromecast button first.")
        }
        val (stream, fragmented) = try {
            withContext(Dispatchers.IO) {
                val stream = client.playback(video.data)
                stream to runCatching { client.fragmentedHls(stream.url) }.getOrDefault(false)
            }
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) { throw ErrorLoadingException(error.message ?: "T18 playback is unavailable.") }
        withContext(Dispatchers.Main) {
            if (CastContext.getSharedInstance(activity).sessionManager.currentCastSession !== session)
                throw ErrorLoadingException("The Chromecast session has changed. Reconnect to Cast.")
            val remote = session.remoteMediaClient ?: throw ErrorLoadingException("The Chromecast session is not ready.")
            val request = T18DirectCastRequest.build(stream.url, video.name ?: "T18 replay", video.position, fragmented)
            val app = activity.applicationContext
            remote.load(request).setResultCallback { response ->
                if (!response.status.isSuccess) Toast.makeText(app,
                    "The TV rejected T18 playback (code ${response.status.statusCode}).", Toast.LENGTH_LONG).show()
            }
        }
    }
}
