package fr.rmcplus

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class RMCCastLicenseTest {
    @Test fun `relay sends the play token and decodes DRMtoday envelopes`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("{\"license\":\"AAEC\"}"))
            server.enqueue(MockResponse().setBody(Buffer().write(byteArrayOf(-2, 0, 8))))
            val license = RMCCastLicense("synthetic-token", server.url("/license-proxy-widevine/cenc/"))
            assertArrayEquals(byteArrayOf(0, 1, 2), license.request(byteArrayOf(0, -1, 42)))
            assertArrayEquals(byteArrayOf(-2, 0, 8), license.request(byteArrayOf(9)))
            val first = server.takeRequest(2, TimeUnit.SECONDS)!!
            assertEquals("POST", first.method)
            assertEquals("/license-proxy-widevine/cenc/", first.path)
            assertEquals("synthetic-token", first.getHeader("x-dt-auth-token"))
            assertArrayEquals(byteArrayOf(0, -1, 42), first.body.readByteArray())
            assertArrayEquals(byteArrayOf(9), server.takeRequest(2, TimeUnit.SECONDS)!!.body.readByteArray())
        }
    }

    @Test fun `relay never follows license redirects`() {
        MockWebServer().use { source ->
            MockWebServer().use { destination ->
                source.enqueue(MockResponse().setResponseCode(307).setHeader("Location", destination.url("/capture")))
                try {
                    RMCCastLicense("private-token", source.url("/license")).request(byteArrayOf(1))
                    fail("Expected redirect rejection")
                } catch (error: RMCException) { assertEquals(307, error.status) }
                assertNull(destination.takeRequest(100, TimeUnit.MILLISECONDS))
            }
        }
    }

    @Test fun `relay rejects empty and malformed license responses`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse())
            server.enqueue(MockResponse().setBody("{\"error\":\"denied\"}"))
            val license = RMCCastLicense("synthetic-token", server.url("/license"))
            assertThrows(IllegalStateException::class.java) { license.request(byteArrayOf(1)) }
            assertThrows(java.io.IOException::class.java) { license.request(byteArrayOf(1)) }
        }
    }
}
