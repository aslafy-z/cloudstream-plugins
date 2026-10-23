package fr.novo19

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Forwards opaque CDM challenges to the HTTPS endpoint from NovoClient; the response is a raw Widevine license. */
internal class NovoCastLicense(private val url: String) {
    private val http = OkHttpClient.Builder()
        // No credentials are sent, but never downgrade a redirected license request to plain HTTP.
        .followSslRedirects(false)
        .callTimeout(30, TimeUnit.SECONDS).build()

    fun request(challenge: ByteArray): ByteArray {
        val request = Request.Builder().url(url)
            .post(challenge.toRequestBody("application/octet-stream".toMediaType())).build()
        return http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("NOVO19 license request failed (HTTP ${response.code}).")
            val body = response.body?.bytes()
            check(body != null && body.isNotEmpty()) { "NOVO19 returned an empty license." }
            body
        }
    }
}
