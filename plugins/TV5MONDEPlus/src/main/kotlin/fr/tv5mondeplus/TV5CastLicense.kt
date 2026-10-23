package fr.tv5mondeplus

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Forwards opaque CDM challenges; the Red Bee endpoint answers with raw Widevine license bytes. */
internal class TV5CastLicense(private val licenseUrl: String) {
    private val http = OkHttpClient.Builder()
        .followRedirects(false).followSslRedirects(false)
        .callTimeout(30, TimeUnit.SECONDS).build()

    fun request(challenge: ByteArray): ByteArray {
        val request = Request.Builder().url(licenseUrl)
            .post(challenge.toRequestBody("application/octet-stream".toMediaType())).build()
        return http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("TV5MONDEplus license request failed (HTTP ${response.code}).")
            val body = response.body?.bytes()
            check(body != null && body.isNotEmpty()) { "TV5MONDEplus returned an empty license." }
            body
        }
    }
}
