package fr.rmcplus

import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/** Adapts DRMtoday's transport envelope; Android's Widevine CDM handles the license. */
internal class RMCLicenseInterceptor(
    private val token: String,
    private val endpoint: HttpUrl = RMCClient.LICENSE.toHttpUrl(),
) : Interceptor {
    private val licenseClient = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .callTimeout(30, TimeUnit.SECONDS).build()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url
        if (url.scheme != endpoint.scheme || url.host != endpoint.host || url.port != endpoint.port ||
            url.encodedPath != endpoint.encodedPath || url.encodedQuery != endpoint.encodedQuery) return chain.proceed(request)
        RMCDiagnostics.record("License requested by player")
        return try {
            val response = licenseClient.newCall(request.newBuilder()
                .header("x-dt-auth-token", token)
                .header("Content-Type", "application/octet-stream")
                .header("Cache-Control", "no-store")
                .build()).execute()
            if (!response.isSuccessful) {
                RMCDiagnostics.record("License request failed")
                return response
            }
            val body = response.body ?: run {
                RMCDiagnostics.record("License request failed")
                return response
            }
            val bytes = body.bytes()
            val license = RMCLicenseResponse.decode(bytes)
            response.newBuilder()
                .removeHeader("Content-Encoding")
                .header("Content-Type", "application/octet-stream")
                .header("Content-Length", license.size.toString())
                .body(license.toResponseBody("application/octet-stream".toMediaType())).build().also {
                    RMCDiagnostics.record("License response accepted")
                }
        } catch (error: Exception) {
            RMCDiagnostics.record("License request failed")
            throw error
        }
    }
}
