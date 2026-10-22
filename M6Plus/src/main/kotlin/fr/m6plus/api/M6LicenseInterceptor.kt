package fr.m6plus.api

import fr.m6plus.diagnostics.M6Diagnostics
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/** Adapts DRMtoday's transport envelope; Android's Widevine CDM handles the license. */
internal class M6LicenseInterceptor(
    private val token: String,
    private val endpoint: HttpUrl = M6PlaybackClient.LICENSE_URL.toHttpUrl(),
) : Interceptor {
    private val licenseClient = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).build()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url
        if (url.scheme != endpoint.scheme || url.host != endpoint.host || url.port != endpoint.port ||
            url.encodedPath != endpoint.encodedPath) return chain.proceed(request)
        M6Diagnostics.record(M6Diagnostics.Event.LOCAL_LICENSE_REQUEST)
        return try {
            val response = licenseClient.newCall(request.newBuilder()
                .header("x-dt-auth-token", token)
                .header("Content-Type", "application/octet-stream")
                .header("Cache-Control", "no-store")
                .build()).execute()
            if (!response.isSuccessful) {
                M6Diagnostics.failure(M6Diagnostics.Operation.LICENSE, M6ApiException(response.code, "License request rejected"))
                return response
            }
            val body = response.body ?: run {
                M6Diagnostics.failure(M6Diagnostics.Operation.LICENSE, IllegalStateException("Missing license body"))
                return response
            }
            val bytes = body.bytes()
            val license = M6LicenseResponse.decode(bytes)
            response.newBuilder()
                .removeHeader("Content-Encoding")
                .header("Content-Type", "application/octet-stream")
                .header("Content-Length", license.size.toString())
                .body(license.toResponseBody("application/octet-stream".toMediaType())).build().also {
                    M6Diagnostics.record(M6Diagnostics.Event.LOCAL_LICENSE_OK)
                }
        } catch (error: Exception) {
            M6Diagnostics.failure(M6Diagnostics.Operation.LICENSE, error)
            throw error
        }
    }
}
