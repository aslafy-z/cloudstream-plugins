package fr.francetv

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Forwards opaque CDM challenges with the france.tv authorization token; the service answers with the raw license. */
internal class FranceTVCastLicense(private val url: String, private val headers: Map<String, String>) {
    // Redirects are refused so the token is only ever sent to the resolved license endpoint.
    private val http = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .callTimeout(30, TimeUnit.SECONDS).build()

    fun request(challenge: ByteArray): ByteArray {
        val request = Request.Builder().url(url).apply { headers.forEach { (key, value) -> header(key, value) } }
            .post(challenge.toRequestBody("application/octet-stream".toMediaType())).build()
        return http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("france.tv license request failed (HTTP ${response.code}).")
            val body = response.body?.bytes()
            check(body != null && body.isNotEmpty()) { "france.tv returned an empty license." }
            body
        }
    }
}
