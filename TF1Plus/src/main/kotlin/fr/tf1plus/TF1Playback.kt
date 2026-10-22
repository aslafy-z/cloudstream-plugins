package fr.tf1plus

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import org.json.JSONObject

internal data class TF1Stream(
    val manifest: String,
    val licenseUrl: String,
    val licenseHeaders: Map<String, String>,
    val subtitles: List<String>
)

internal object TF1PlaybackPayload {
    fun parse(json: JSONObject, videoId: String): TF1Stream {
        val delivery = json.optJSONObject("delivery")
            ?: throw IllegalStateException("Missing TF1+ video delivery.")
        val code = delivery.optInt("code", 200)
        if (code >= 400) throw IllegalStateException("TF1+ video unavailable (delivery code $code).")
        val manifest = delivery.stringOrNull("url")
            ?: throw IllegalStateException("TF1+ did not provide a playback manifest.")
        require(manifest.startsWith("https://")) { "TF1+ returned an unsupported media URL." }

        val drms = delivery.optJSONArray("drms")
        val drm = (0 until (drms?.length() ?: 0))
            .mapNotNull { drms?.optJSONObject(it) }
            .firstOrNull { it.stringOrNull("url") != null }
        val licenseUrl = drm?.stringOrNull("url")
            ?: "https://drm-wide.tf1.fr/proxy?id=${TF1Http.enc(videoId)}"
        require(licenseUrl.startsWith("https://")) { "TF1+ returned an unsupported DRM URL." }
        val headers = linkedMapOf<String, String>()
        val rawHeaders = drm?.optJSONArray("h")
        for (i in 0 until (rawHeaders?.length() ?: 0)) {
            val item = rawHeaders?.optJSONObject(i) ?: continue
            val key = item.stringOrNull("k") ?: item.stringOrNull("key") ?: "Authorization"
            val value = item.stringOrNull("v") ?: item.stringOrNull("value") ?: continue
            if (key.matches(Regex("[A-Za-z0-9-]+")) && key.lowercase() !in setOf("host", "content-length")) {
                headers[key] = value
            }
        }
        val subtitles = delivery.optJSONArray("subtitles")?.jsonObjects().orEmpty()
            .filter { it.stringOrNull("language")?.lowercase() in setOf("fr", "fra", "french") }
            .mapNotNull { it.stringOrNull("url") }
            .filter { it.startsWith("https://") }
        return TF1Stream(manifest, licenseUrl, headers, subtitles)
    }
}

internal class TF1Playback(private val http: TF1Http, private val auth: TF1Auth) {
    fun resolve(videoId: String): TF1Stream {
        val url = TF1Http.query("https://mediainfo.tf1.fr/mediainfocombo/${TF1Http.enc(videoId)}",
            mapOf("context" to "MYTF1", "pver" to "5010000", "platform" to "web",
                "device" to "desktop", "os" to "linux", "osVersion" to "unknown",
                "topDomain" to TF1Http.ROOT, "playerVersion" to "5.19.0",
                "productName" to "mytf1", "productVersion" to "3.22.0"))
        fun fetch(refresh: Boolean) =
            JSONObject(http.get(url, mapOf("Authorization" to "Bearer ${auth.token(refresh)}")))
        val response = try { fetch(false) } catch (e: TF1HttpException) {
            if (e.status != 401) throw e
            fetch(true)
        }
        return TF1PlaybackPayload.parse(response, videoId)
    }
}

/** Attach provider-supplied DRM headers only to the exact license endpoint, never media/CDN URLs. */
internal class TF1LicenseInterceptor(
    private val licenseUrl: String, private val headers: Map<String, String>
) : Interceptor {
    private val target = licenseUrl.toHttpUrlOrNull()
        ?: throw IllegalArgumentException("Invalid TF1+ license endpoint")

    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val url = chain.request().url
        if (url.scheme != target.scheme || url.host != target.host ||
            url.port != target.port || url.encodedPath != target.encodedPath ||
            url.encodedQuery != target.encodedQuery) return chain.proceed(chain.request())
        val builder = chain.request().newBuilder()
        headers.forEach { (key, value) -> builder.header(key, value) }
        return chain.proceed(builder.build())
    }
}
