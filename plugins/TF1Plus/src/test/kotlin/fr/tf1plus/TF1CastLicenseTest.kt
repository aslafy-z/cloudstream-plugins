package fr.tf1plus

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class TF1CastLicenseTest {
    @Test fun `relay preserves binary challenges and licenses with provider headers`() {
        MockWebServer().use { server ->
            val challenge = byteArrayOf(0, -1, 42)
            val license = byteArrayOf(-2, 0, 8)
            server.enqueue(MockResponse().setBody(Buffer().write(license)))
            val stream = TF1Stream("https://cdn.test/replay.mpd", server.url("/license?id=test").toString(),
                mapOf("Authorization" to "Bearer test-token"), emptyList())
            assertArrayEquals(license, TF1CastLicense(stream).request(challenge))
            val request = server.takeRequest(2, TimeUnit.SECONDS)!!
            assertEquals("POST", request.method)
            assertEquals("/license?id=test", request.path)
            assertEquals("Bearer test-token", request.getHeader("Authorization"))
            assertArrayEquals(challenge, request.body.readByteArray())
        }
    }

    @Test fun `relay never follows license redirects or sends credentials to another endpoint`() {
        MockWebServer().use { source ->
            MockWebServer().use { destination ->
                source.enqueue(MockResponse().setResponseCode(307).setHeader("Location", destination.url("/capture")))
                val stream = TF1Stream("https://cdn.test/replay.mpd", source.url("/license").toString(),
                    mapOf("Authorization" to "Bearer private-token"), emptyList())
                try {
                    TF1CastLicense(stream).request(byteArrayOf(1))
                    fail("Expected redirect rejection")
                } catch (error: TF1HttpException) { assertEquals(307, error.status) }
                assertNull(destination.takeRequest(100, TimeUnit.MILLISECONDS))
            }
        }
    }

    @Test fun `relay rejects empty license responses`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse())
            val stream = TF1Stream("https://cdn.test/replay.mpd", server.url("/license").toString(), emptyMap(), emptyList())
            try {
                TF1CastLicense(stream).request(byteArrayOf(1))
                fail("Expected empty license rejection")
            } catch (_: IllegalStateException) { }
        }
    }
}
