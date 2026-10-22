package fr.tf1plus

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class TF1LicenseInterceptorTest {
    @Test fun licenseHeadersAreRestrictedToExactEndpointIncludingQuery() {
        MockWebServer().use { server ->
            server.start()
            val endpoint = server.url("/license?id=one")
            val client = OkHttpClient.Builder().addInterceptor(
                TF1LicenseInterceptor(endpoint.toString(), mapOf("x-license-token" to "private"))).build()
            for (url in listOf(endpoint, server.url("/license?id=two"), server.url("/video.mpd"))) {
                server.enqueue(MockResponse().setBody("binary"))
                client.newCall(Request.Builder().url(url).build()).execute().close()
                assertEquals(if (url == endpoint) "private" else null,
                    server.takeRequest().getHeader("x-license-token"))
            }
        }
    }

    @Test fun redirectsDoNotLeakHeadersAndErrorsDoNotLogSuccess() {
        MockWebServer().use { server -> MockWebServer().use { other ->
            server.start()
            other.start()
            val endpoint = server.url("/license")
            val client = OkHttpClient.Builder().addInterceptor(
                TF1LicenseInterceptor(endpoint.toString(), mapOf("x-license-token" to "private"))).build()
            TF1Diagnostics.clear()
            server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", other.url("/leak")))
            client.newCall(Request.Builder().url(endpoint).build()).execute().use {
                assertEquals(302, it.code)
            }
            assertEquals(0, other.requestCount)
            assertTrue(TF1Diagnostics.snapshot().contains("failed (HTTP 302)"))
            assertFalse(TF1Diagnostics.snapshot().contains("succeeded"))
        } }
    }
}
