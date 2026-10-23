package fr.francetv

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit

class FranceTVCastLicenseTest {
    private val headers = mapOf("nv-authorizations" to "test-token", "Origin" to "https://www.france.tv",
        "Referer" to "https://www.france.tv/", "User-Agent" to FranceTVClient.USER_AGENT)

    @Test fun `binary challenge is forwarded with the authorization token and the raw license returned`() {
        MockWebServer().use { server ->
            val challenge = byteArrayOf(8, 1, 0, -1, 42)
            val license = byteArrayOf(-2, 0, 8)
            server.enqueue(MockResponse().setHeader("Content-Type", "application/octet-stream").setBody(Buffer().write(license)))
            assertArrayEquals(license, FranceTVCastLicense(server.url("/v1/licenses/").toString(), headers).request(challenge))
            val request = server.takeRequest(2, TimeUnit.SECONDS)!!
            assertEquals("POST", request.method)
            assertEquals("/v1/licenses/", request.path)
            assertEquals("test-token", request.getHeader("nv-authorizations"))
            assertEquals("https://www.france.tv", request.getHeader("Origin"))
            assertEquals("https://www.france.tv/", request.getHeader("Referer"))
            assertEquals(FranceTVClient.USER_AGENT, request.getHeader("User-Agent"))
            assertArrayEquals(challenge, request.body.readByteArray())
        }
    }

    @Test fun `license redirects are not followed so the token stays on the license endpoint`() {
        MockWebServer().use { source ->
            MockWebServer().use { destination ->
                source.enqueue(MockResponse().setResponseCode(307).setHeader("Location", destination.url("/capture")))
                val error = assertThrows(IOException::class.java) {
                    FranceTVCastLicense(source.url("/license").toString(), headers).request(byteArrayOf(1))
                }
                assertTrue(error.message!!.contains("307"))
                assertNull(destination.takeRequest(100, TimeUnit.MILLISECONDS))
            }
        }
    }

    @Test fun `empty license responses are rejected`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse())
            assertThrows(IllegalStateException::class.java) {
                FranceTVCastLicense(server.url("/license").toString(), headers).request(byteArrayOf(1))
            }
        }
    }
}
