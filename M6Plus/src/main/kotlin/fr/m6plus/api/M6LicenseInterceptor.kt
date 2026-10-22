package fr.m6plus.api

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.ByteString.Companion.decodeBase64
import org.json.JSONObject
import java.io.IOException

/** Adapts DRMtoday's transport envelope; Android's Widevine CDM handles the license. */
internal class M6LicenseInterceptor(
    private val token: String,
    private val endpoint: HttpUrl = M6PlaybackClient.LICENSE_URL.toHttpUrl(),
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url
        if (url.scheme != endpoint.scheme || url.host != endpoint.host || url.port != endpoint.port ||
            url.encodedPath != endpoint.encodedPath) return chain.proceed(request)
        val response = chain.proceed(request.newBuilder()
            .header("x-dt-auth-token", token)
            .header("Content-Type", "application/octet-stream")
            .header("Cache-Control", "no-store")
            .build())
        if (!response.isSuccessful) return response
        val body = response.body ?: return response
        val bytes = body.bytes()
        val payload = bytes.toString(Charsets.UTF_8).trim()
        val license = if (payload.startsWith("{")) {
            val encoded = try { JSONObject(payload).text("license") } catch (_: Exception) { null }
            encoded?.decodeBase64()?.toByteArray() ?: throw IOException("Réponse de licence M6+ invalide.")
        } else bytes
        return response.newBuilder()
            .removeHeader("Content-Encoding")
            .header("Content-Type", "application/octet-stream")
            .header("Content-Length", license.size.toString())
            .body(license.toResponseBody("application/octet-stream".toMediaType())).build()
    }
}
