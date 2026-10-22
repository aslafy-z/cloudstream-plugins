package fr.m6plus.api

import fr.m6plus.diagnostics.M6Diagnostics
import fr.m6plus.auth.M6SessionManager
import fr.m6plus.model.M6Playback
import org.json.JSONArray
import org.json.JSONObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

internal class M6PlaybackClient(private val http: M6ApiClient, private val sessions: M6SessionManager,
    private val licenseEndpoint: String = LICENSE_URL) {
    private val licenseClient = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .callTimeout(30, TimeUnit.SECONDS).build()
    fun resolve(videoId: String): M6Playback = M6Diagnostics.trace(M6Diagnostics.Operation.PLAYBACK) {
        val token = upfront(videoId, false)
        val url = "https://android.middleware.6play.fr/6play/v2/platforms/m6group_androidmob/services/6play/videos/${M6ApiClient.encode(videoId)}?csa=6&with=clips,freemiumpacks"
        val video = JSONObject(http.get(url, mapOf("x-customer-name" to "m6web")))
        val assets = video.optJSONArray("clips")?.optJSONObject(0)?.optJSONArray("assets")?.objects().orEmpty()
        val dash = assets.filter { it.text("type") == "usp_dashcenc_h264" && it.text("full_physical_path") != null }
            .maxByOrNull { when (it.text("video_quality")?.lowercase()) { "hd" -> 2; "sd" -> 1; else -> 0 } }
            ?: throw IllegalStateException("No compatible DASH Widevine stream is available for this video.")
        val manifest = http.redirect(dash.text("full_physical_path")!!)
        val subtitles = assets.filter { it.text("type")?.contains("subtitle_vtt") == true }
            .mapNotNull { it.text("full_physical_path") }
        return@trace M6Playback(manifest, token, subtitles)
    }

    fun license(videoId: String, challenge: ByteArray): ByteArray = M6Diagnostics.trace(M6Diagnostics.Operation.LICENSE) {
        // Each CDM request gets a fresh upfront token, including separate audio/video licenses.
        val request = Request.Builder().url(licenseEndpoint)
            .header("x-dt-auth-token", upfront(videoId, false))
            .post(challenge.toRequestBody("application/octet-stream".toMediaType())).build()
        return@trace licenseClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw M6ApiException(response.code, "M6+ license unavailable (${response.code}).")
            M6LicenseResponse.decode(response.body?.bytes() ?: throw IllegalStateException("Empty M6+ license."))
        }
    }

    private fun upfront(videoId: String, retry: Boolean): String {
        val session = sessions.session(retry)
        val url = "https://drm.6cloud.fr/v1/customers/m6web/platforms/m6group_web/services/m6replay/users/${M6ApiClient.encode(session.uid)}/videos/${M6ApiClient.encode(videoId)}/upfront-token"
        try {
            return JSONObject(http.get(url, mapOf("X-Customer-Name" to "m6web",
                "X-Client-Release" to "5.103.3", "Authorization" to "Bearer ${session.jwt}")))
                .text("token") ?: throw IllegalStateException("Missing M6+ playback token.")
        } catch (error: M6ApiException) {
            if (error.status == 401 && !retry) return upfront(videoId, true)
            throw error
        }
    }

    companion object { const val LICENSE_URL = "https://lic.drmtoday.com/license-proxy-widevine/cenc/" }
}
