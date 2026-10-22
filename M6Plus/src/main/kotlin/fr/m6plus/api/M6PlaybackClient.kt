package fr.m6plus.api

import fr.m6plus.auth.M6SessionManager
import fr.m6plus.model.M6Playback
import org.json.JSONArray
import org.json.JSONObject

internal class M6PlaybackClient(private val http: M6ApiClient, private val sessions: M6SessionManager) {
    fun resolve(videoId: String): M6Playback {
        val token = upfront(videoId, false)
        val url = "https://android.middleware.6play.fr/6play/v2/platforms/m6group_androidmob/services/6play/videos/${M6ApiClient.encode(videoId)}?csa=6&with=clips,freemiumpacks"
        val video = JSONObject(http.get(url, mapOf("x-customer-name" to "m6web")))
        val assets = video.optJSONArray("clips")?.optJSONObject(0)?.optJSONArray("assets")?.objects().orEmpty()
        val dash = assets.filter { it.text("type") == "usp_dashcenc_h264" && it.text("full_physical_path") != null }
            .maxByOrNull { when (it.text("video_quality")?.lowercase()) { "hd" -> 2; "sd" -> 1; else -> 0 } }
            ?: throw IllegalStateException("Aucun flux DASH Widevine compatible pour cette vidéo.")
        val manifest = http.redirect(dash.text("full_physical_path")!!)
        val subtitles = assets.filter { it.text("type")?.contains("subtitle_vtt") == true }
            .mapNotNull { it.text("full_physical_path") }
        return M6Playback(manifest, token, subtitles)
    }

    private fun upfront(videoId: String, retry: Boolean): String {
        val session = sessions.session(retry)
        val url = "https://drm.6cloud.fr/v1/customers/m6web/platforms/m6group_web/services/m6replay/users/${M6ApiClient.encode(session.uid)}/videos/${M6ApiClient.encode(videoId)}/upfront-token"
        try {
            return JSONObject(http.get(url, mapOf("X-Customer-Name" to "m6web",
                "X-Client-Release" to "5.103.3", "Authorization" to "Bearer ${session.jwt}")))
                .text("token") ?: throw IllegalStateException("Jeton de lecture M6+ absent.")
        } catch (error: M6ApiException) {
            if (error.status == 401 && !retry) return upfront(videoId, true)
            throw error
        }
    }

    companion object { const val LICENSE_URL = "https://lic.drmtoday.com/license-proxy-widevine/cenc/" }
}
