package fr.rmcplus

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** Forwards opaque CDM challenges to DRMtoday with the play token and returns the decoded license. */
internal class RMCCastLicense(private val token: String, private val endpoint: HttpUrl = RMCClient.LICENSE.toHttpUrl()) {
    private val http = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .callTimeout(30, TimeUnit.SECONDS).build()

    fun request(challenge: ByteArray): ByteArray {
        val request = Request.Builder().url(endpoint)
            .header("x-dt-auth-token", token)
            .header("Cache-Control", "no-store")
            .post(challenge.toRequestBody("application/octet-stream".toMediaType())).build()
        return http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw RMCException(response.code, "RMC+ license unavailable (HTTP ${response.code}).")
            val body = response.body?.bytes()
            check(body != null && body.isNotEmpty()) { "RMC+ returned an empty license." }
            RMCLicenseResponse.decode(body)
        }
    }
}
