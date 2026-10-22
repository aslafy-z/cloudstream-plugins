package fr.tf1plus

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import org.json.JSONObject

internal data class TF1Stream(
    val manifest: String,
    val licenseUrl: String,
    val licenseHeaders: Map<String, String>,
    val subtitles: List<String>
)

internal class TF1DeliveryException(val code: Int) : IllegalStateException(
    "TF1+ video unavailable (delivery code $code)."
)

internal object TF1PlaybackPayload {
    fun parse(json: JSONObject, videoId: String): TF1Stream {
        val delivery = json.optJSONObject("delivery")
            ?: throw IllegalStateException("Missing TF1+ video delivery.")
        val code = delivery.optInt("code", 200)
        if (code >= 400) throw TF1DeliveryException(code)
        val manifest = delivery.stringOrNull("url")
            ?: throw IllegalStateException("TF1+ did not provide a playback manifest.")
        require(manifest.startsWith("https://")) { "TF1+ returned an unsupported media URL." }

        require(delivery.stringOrNull("format")?.lowercase() in setOf(null, "dash")) {
            "TF1+ did not return a DASH stream."
        }
        val drms = delivery.optJSONArray("drms")
        val drm = (0 until (drms?.length() ?: 0))
            .mapNotNull { drms?.optJSONObject(it) }
            .firstOrNull { item ->
                item.stringOrNull("name")?.equals("widevine", ignoreCase = true) == true ||
                    (item.stringOrNull("name") == null &&
                        item.stringOrNull("url")?.toHttpUrlOrNull()?.host == "drm-wide.tf1.fr")
            }
        check(drm != null || drms == null || drms.length() == 0) {
            "TF1+ did not provide a Widevine license."
        }
        val licenseUrl = drm?.stringOrNull("url")
            ?: "https://drm-wide.tf1.fr/proxy?id=${TF1Http.enc(videoId)}"
        require(licenseUrl.startsWith("https://")) { "TF1+ returned an unsupported DRM URL." }
        val headers = linkedMapOf<String, String>()
        val rawHeaders = drm?.optJSONArray("h")
        for (i in 0 until (rawHeaders?.length() ?: 0)) {
            val item = rawHeaders?.optJSONObject(i) ?: continue
            val key = item.stringOrNull("k") ?: item.stringOrNull("key") ?: "Authorization"
            val value = item.stringOrNull("v") ?: item.stringOrNull("value") ?: continue
            if (key.matches(Regex("[A-Za-z0-9-]+")) && value.none { it == '\r' || it == '\n' } &&
                key.lowercase() !in setOf("host", "content-length", "connection", "transfer-encoding")) {
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
        fun fetch(refresh: Boolean) = TF1PlaybackPayload.parse(
            JSONObject(http.get(url, mapOf("Authorization" to "Bearer ${auth.token(refresh)}"))), videoId)
        return try { fetch(false) } catch (error: Exception) {
            val status = when (error) {
                is TF1HttpException -> error.status
                is TF1DeliveryException -> error.code
                else -> throw error
            }
            if (status !in setOf(401, 403)) throw error
            fetch(true)
        }
    }
}

/** Attach provider-supplied DRM headers only to the exact license endpoint, never media/CDN URLs. */
internal class TF1LicenseInterceptor(
    private val licenseUrl: String, private val headers: Map<String, String>
) : Interceptor {
    private val target = licenseUrl.toHttpUrlOrNull()
        ?: throw IllegalArgumentException("Invalid TF1+ license endpoint")

    private val licenseClient = OkHttpClient.Builder()
        .followRedirects(false).followSslRedirects(false)
        .callTimeout(30, TimeUnit.SECONDS).build()

    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val url = chain.request().url
        if (url.scheme != target.scheme || url.host != target.host ||
            url.port != target.port || url.encodedPath != target.encodedPath ||
            url.encodedQuery != target.encodedQuery) return chain.proceed(chain.request())
        TF1Diagnostics.record(TF1Diagnostics.Event.LICENSE_REQUEST)
        val builder = chain.request().newBuilder()
        headers.forEach { (key, value) -> builder.header(key, value) }
        return try {
            licenseClient.newCall(builder.build()).execute().also { response ->
                if (response.isSuccessful) TF1Diagnostics.record(TF1Diagnostics.Event.LICENSE_OK)
                else TF1Diagnostics.failure(TF1Diagnostics.Operation.LICENSE, TF1HttpException(response.code))
            }
        } catch (error: Exception) {
            TF1Diagnostics.failure(TF1Diagnostics.Operation.LICENSE, error)
            throw error
        }
    }
}
