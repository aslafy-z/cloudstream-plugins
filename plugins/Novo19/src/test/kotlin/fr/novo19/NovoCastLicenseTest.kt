package fr.novo19

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit

class NovoCastLicenseTest {
    @Test fun `relay forwards binary challenges and returns the raw license without credentials`() {
        MockWebServer().use { server ->
            val challenge = byteArrayOf(0, -1, 42)
            val license = byteArrayOf(-2, 0, 8)
            server.enqueue(MockResponse().setBody(Buffer().write(license)))
            assertArrayEquals(license, NovoCastLicense(server.url("/widevine?asset=test").toString()).request(challenge))
            val request = server.takeRequest(2, TimeUnit.SECONDS)!!
            assertEquals("POST", request.method)
            assertEquals("/widevine?asset=test", request.path)
            assertNull(request.getHeader("Authorization"))
            assertNull(request.getHeader("Cookie"))
            assertArrayEquals(challenge, request.body.readByteArray())
        }
    }

    @Test fun `license errors report the upstream status`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(403))
            val error = assertThrows(IOException::class.java) { NovoCastLicense(server.url("/license").toString()).request(byteArrayOf(1)) }
            assertTrue(error.message!!.contains("403"))
        }
    }

    @Test fun `relay rejects empty license responses`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse())
            assertThrows(IllegalStateException::class.java) { NovoCastLicense(server.url("/license").toString()).request(byteArrayOf(1)) }
        }
    }
}
