package fr.t18

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.*
import org.junit.Test

class T18HlsTest {
    @Test fun `first variant resolves against the master address without downgrading to http`() {
        val master = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\nhttps://cdn.test/sec(x)/fmp4/1/manifest.m3u8\n#EXT-X-MEDIA:TYPE=SUBTITLES,URI=\"https://cdn.test/sub.m3u8\"\n"
        assertEquals("https://cdn.test/sec(x)/fmp4/1/manifest.m3u8", T18Client.variant(master, "https://director.test/master.m3u8"))
        assertEquals("https://director.test/v/low.m3u8", T18Client.variant("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\n\nlow.m3u8\n", "https://director.test/v/master.m3u8"))
        assertNull(T18Client.variant("#EXTM3U\n#EXTINF:3,\n0.ts\n", "https://director.test/media.m3u8"))
        assertNull(T18Client.variant("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\nhttp://cdn.test/plain.m3u8\n", "https://director.test/master.m3u8"))
        assertNull(T18Client.variant("#EXTM3U\n", "https://director.test/master.m3u8"))
    }

    @Test fun `fragmented playlists are detected from their init segment map`() {
        assertTrue(T18Client.fragmented("#EXTM3U\n#EXT-X-MAP:URI=\"init.mp4\"\n#EXTINF:3,\n0.m4s\n"))
        assertFalse(T18Client.fragmented("#EXTM3U\n#EXTINF:3,\n0.ts\n"))
    }

    @Test fun `playlist probe sends Dailymotion player headers and follows the variant`() {
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest) = when (request.path) {
                    "/master.m3u8" -> MockResponse().setBody("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\nv/media.m3u8\n")
                    "/v/media.m3u8" -> MockResponse().setBody("#EXTM3U\n#EXT-X-MAP:URI=\"init.mp4\"\n0.m4s\n")
                    "/ts.m3u8" -> MockResponse().setBody("#EXTM3U\n#EXTINF:3,\n0.ts\n")
                    else -> MockResponse().setResponseCode(404)
                }
            }
            val client = T18Client()
            assertTrue(client.fragmentedHls(server.url("/master.m3u8").toString()))
            val first = server.takeRequest()
            assertEquals("https://www.dailymotion.com/", first.getHeader("Referer"))
            assertEquals("https://www.dailymotion.com", first.getHeader("Origin"))
            assertEquals(T18Client.UA, first.getHeader("User-Agent"))
            assertEquals("/v/media.m3u8", server.takeRequest().path)
            assertFalse(client.fragmentedHls(server.url("/ts.m3u8").toString()))
        }
    }

    @Test fun `oversized playlists are refused before being read`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("#".repeat(1_048_577)))
            assertThrows(java.io.IOException::class.java) { T18Client().fragmentedHls(server.url("/big.m3u8").toString()) }
        }
    }
}
