package fr.m6plus.cast

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.actions.VideoClickAction
import com.lagradost.cloudstream3.ui.result.LinkLoadingResult
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.utils.txt
import fr.m6plus.M6Runtime
import fr.m6plus.diagnostics.M6Diagnostics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.coroutines.resume

class M6NativeCastAction : VideoClickAction() {
    override val name = txt("M6+ native Chromecast (with ads)")
    override fun shouldShow(context: Context?, video: ResultEpisode?): Boolean = context != null && video?.apiName == "M6+"

    override suspend fun runAction(context: Context?, video: ResultEpisode, result: LinkLoadingResult, index: Int?) {
        val activity = context ?: throw ErrorLoadingException("Open the replay details to start Chromecast.")
        val request = try { withContext(Dispatchers.IO) { M6Runtime.nativeClient.prepare(video.data) } }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { throw ErrorLoadingException("Unable to connect to the native M6+ player. Check your M6+ account and profiles.") }
        withContext(Dispatchers.Main) {
            val profile = selectProfile(activity, request.profiles) ?: return@withContext
            try { M6Runtime.nativeCast.start(activity, request.payload(profile), video.position / 1000.0) }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) {
                M6Diagnostics.record(M6Diagnostics.Event.NATIVE_FAILED)
                M6Runtime.nativeCast.stop()
                throw ErrorLoadingException("Unable to connect to native Chromecast. Check Google Cast services and Wi-Fi.")
            }
        }
    }

    private suspend fun selectProfile(context: Context, profiles: List<JSONObject>): JSONObject? {
        if (profiles.size == 1) return profiles.single()
        return suspendCancellableCoroutine { continuation ->
            val dialog = AlertDialog.Builder(context).setTitle("M6+ profile")
                .setItems(profiles.mapIndexed { index, profile -> profile.optString("username", "Profile ${index + 1}") }.toTypedArray()) { _, index ->
                    if (continuation.isActive) continuation.resume(profiles[index])
                }
                .setNegativeButton("Cancel") { _, _ -> if (continuation.isActive) continuation.resume(null) }
                .setOnCancelListener { if (continuation.isActive) continuation.resume(null) }.create()
            continuation.invokeOnCancellation { android.os.Handler(android.os.Looper.getMainLooper()).post { dialog.dismiss() } }
            dialog.show()
        }
    }
}
