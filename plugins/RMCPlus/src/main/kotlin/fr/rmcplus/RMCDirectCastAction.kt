package fr.rmcplus

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
class RMCDirectCastAction : VideoClickAction() {
    override val name = txt("RMC+ direct Chromecast (relay)")
    override fun shouldShow(context: Context?, video: ResultEpisode?): Boolean = context != null && video?.apiName == "RMC+"

    override suspend fun runAction(context: Context?, video: ResultEpisode, result: LinkLoadingResult, index: Int?) {
        val activity = context ?: throw ErrorLoadingException("Open the replay details to start Chromecast.")
        RMCDiagnostics.record(RMCDiagnostics.Cast.SESSION)
        val session = withContext(Dispatchers.Main) {
            RMCRuntime.cast.observe()
            CastContext.getSharedInstance(activity).sessionManager.currentCastSession?.takeIf {
                it.applicationMetadata?.applicationId == CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID
            } ?: throw ErrorLoadingException("Stop native Cast, then connect CloudStream to the TV using its Chromecast button.")
        }
        RMCDiagnostics.record(RMCDiagnostics.Cast.PREPARING)
        val stream = try { withContext(Dispatchers.IO) { RMCRuntime.client.playback(video.data, RMCRuntime.auth) } }
            catch (error: CancellationException) { throw error }
            catch (error: RMCException) { throw ErrorLoadingException(error.message ?: "RMC+ playback unavailable.") }
        RMCRuntime.requireCastable(stream)
        try {
            val url = RMCRuntime.castUrl(stream)
            withContext(Dispatchers.Main) {
                check(CastContext.getSharedInstance(activity).sessionManager.currentCastSession === session) {
                    "The Chromecast session has changed."
                }
                val client = checkNotNull(session.remoteMediaClient)
                val type = if (stream.dash) "application/dash+xml" else "application/x-mpegurl"
                val request = RMCDirectCastRequest.build(url, video.name ?: "RMC+ replay", video.position, type)
                RMCDiagnostics.record(RMCDiagnostics.Cast.LOAD_SENT)
                client.load(request).setResultCallback { response ->
                    if (response.status.isSuccess) RMCDiagnostics.record(RMCDiagnostics.Cast.LOAD_ACCEPTED)
                    else {
                        RMCDiagnostics.rejected(response.status.statusCode)
                        RMCRuntime.castMessage("The TV rejected playback (code ${response.status.statusCode}). Check Plugin diagnostics in the RMC+ settings.")
                    }
                }
            }
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) {
            val stage = RMCDiagnostics.failed()
            throw ErrorLoadingException("The Chromecast relay failed ($stage). Check Plugin diagnostics in the RMC+ settings.")
        }
    }
}
