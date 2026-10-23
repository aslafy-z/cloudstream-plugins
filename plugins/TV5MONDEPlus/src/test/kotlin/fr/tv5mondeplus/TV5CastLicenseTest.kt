package fr.tv5mondeplus

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit

class TV5CastLicenseTest {
    @Test fun `relay forwards binary challenges and returns the raw license`() {
        MockWebServer().use { server ->
            val challenge = byteArrayOf(0, -1, 42)
            val license = byteArrayOf(8, 5, -2, 0)
            server.enqueue(MockResponse().setHeader("Content-Type", "application/octet-stream").setBody(Buffer().write(license)))
            assertArrayEquals(license, TV5CastLicense(server.url("/widevine?contentId=test").toString()).request(challenge))
            val request = server.takeRequest(2, TimeUnit.SECONDS)!!
            assertEquals("POST", request.method)
            assertEquals("/widevine?contentId=test", request.path)
            assertEquals("application/octet-stream", request.getHeader("Content-Type"))
            assertNull(request.getHeader("Authorization"))
            assertArrayEquals(challenge, request.body.readByteArray())
        }
    }

    @Test fun `relay never follows license redirects`() {
        MockWebServer().use { source ->
            MockWebServer().use { destination ->
                source.enqueue(MockResponse().setResponseCode(307).setHeader("Location", destination.url("/capture")))
                assertThrows(IOException::class.java) { TV5CastLicense(source.url("/widevine").toString()).request(byteArrayOf(1)) }
                assertNull(destination.takeRequest(100, TimeUnit.MILLISECONDS))
            }
        }
    }

    @Test fun `relay rejects failed and empty license responses`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(403).setBody("{\"httpCode\":403}"))
            server.enqueue(MockResponse())
            val license = TV5CastLicense(server.url("/widevine").toString())
            assertThrows(IOException::class.java) { license.request(byteArrayOf(1)) }
            assertThrows(IllegalStateException::class.java) { license.request(byteArrayOf(1)) }
        }
    }
}
