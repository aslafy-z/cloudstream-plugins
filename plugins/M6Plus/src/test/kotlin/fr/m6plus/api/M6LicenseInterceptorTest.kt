package fr.m6plus.api

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class M6LicenseInterceptorTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @Before fun setup() {
        server = MockWebServer().apply { start() }
        client = OkHttpClient.Builder().addInterceptor(M6LicenseInterceptor("test-token", server.url("/license"))).build()
    }
    @After fun tearDown() { server.shutdown() }

    @Test fun `license requests carry auth and JSON license is decoded`() {
        server.enqueue(MockResponse().setBody("{\"license\":\"AQIDBA==\"}"))
        client.newCall(Request.Builder().url(server.url("/license")).build()).execute().use {
            assertArrayEquals(byteArrayOf(1, 2, 3, 4), it.body!!.bytes())
            assertEquals("4", it.header("Content-Length"))
        }
        assertEquals("test-token", server.takeRequest().getHeader("x-dt-auth-token"))
    }

    @Test fun `media requests never receive the license token`() {
        server.enqueue(MockResponse().setBody("manifest"))
        client.newCall(Request.Builder().url(server.url("/media.mpd")).build()).execute().use {
            assertEquals("manifest", it.body!!.string())
        }
        assertNull(server.takeRequest().getHeader("x-dt-auth-token"))
    }

    @Test fun `binary license passes through without conversion`() {
        server.enqueue(MockResponse().setBody(okio.Buffer().write(byteArrayOf(0, 1, 2, -1))))
        client.newCall(Request.Builder().url(server.url("/license")).build()).execute().use {
            assertArrayEquals(byteArrayOf(0, 1, 2, -1), it.body!!.bytes())
        }
    }

    @Test fun `rights errors remain errors for the player`() {
        server.enqueue(MockResponse().setResponseCode(403).setBody("denied"))
        client.newCall(Request.Builder().url(server.url("/license")).build()).execute().use {
            assertEquals(403, it.code)
            assertEquals("denied", it.body!!.string())
        }
    }
    @Test fun `license redirects do not forward credentials to another server`() {
        MockWebServer().use { other ->
            other.start()
            server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", other.url("/stolen")))
            client.newCall(Request.Builder().url(server.url("/license")).build()).execute().use {
                assertEquals(302, it.code)
            }
            assertEquals(0, other.requestCount)
        }
    }
    @Test fun `different license query does not receive the token`() {
        server.enqueue(MockResponse().setBody("other"))
        client.newCall(Request.Builder().url(server.url("/license?other=1")).build()).execute().close()
        assertNull(server.takeRequest().getHeader("x-dt-auth-token"))
    }
}
