package fr.tf1plus

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** Forward opaque CDM challenges using the same endpoint restrictions as local playback. */
internal class TF1CastLicense(private val stream: TF1Stream) {
    private val http = OkHttpClient.Builder()
        .followRedirects(false).followSslRedirects(false)
        .callTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(TF1LicenseInterceptor(stream.licenseUrl, stream.licenseHeaders)).build()

    fun request(challenge: ByteArray): ByteArray {
        val request = Request.Builder().url(stream.licenseUrl)
            .post(challenge.toRequestBody("application/octet-stream".toMediaType())).build()
        return http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw TF1HttpException(response.code)
            val body = response.body?.bytes()
            check(body != null && body.isNotEmpty()) { "TF1+ returned an empty license." }
            body
        }
    }
}
