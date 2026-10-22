package fr.m6plus.api

import fr.m6plus.auth.M6SessionManager
import fr.m6plus.auth.M6StoredAccount
import fr.m6plus.auth.MemorySessionStore
import fr.m6plus.auth.jwt
import org.junit.Assert.*
import org.junit.Test
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer

class M6PlaybackClientTest {
    @Test fun `Cast obtains a fresh token for each CDM challenge and decodes license envelopes`() {
        var tokens = 0
        val store = MemorySessionStore().apply { account = M6StoredAccount("email", "pw", M6Session("user", jwt(2000))) }
        val sessions = M6SessionManager(M6AuthGateway { _, _, _ -> error("Unexpected login") }, store) { 1000 }
        val http = object : M6ApiClient() {
            override fun get(url: String, headers: Map<String, String>, cacheMs: Long): String = "{\"token\":\"token-${++tokens}\"}"
        }
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("{\"license\":\"AAEC\"}"))
            server.enqueue(MockResponse().setBody(Buffer().write(byteArrayOf(3, 4))))
            val client = M6PlaybackClient(http, sessions, server.url("/license").toString())
            assertArrayEquals(byteArrayOf(0, 1, 2), client.license("clip_10", byteArrayOf(8)))
            assertArrayEquals(byteArrayOf(3, 4), client.license("clip_10", byteArrayOf(9)))
            val first = server.takeRequest()
            val second = server.takeRequest()
            assertEquals("token-1", first.getHeader("x-dt-auth-token"))
            assertEquals("token-2", second.getHeader("x-dt-auth-token"))
            assertArrayEquals(byteArrayOf(8), first.body.readByteArray())
            assertArrayEquals(byteArrayOf(9), second.body.readByteArray())
        }
    }

    @Test fun `401 refreshes session once and selects HD despite unknown qualities`() {
        var logins = 0
        var tokenRequests = 0
        val store = MemorySessionStore().apply { account = M6StoredAccount("email", "pw", M6Session("user", jwt(2000))) }
        val sessions = M6SessionManager(M6AuthGateway { _, _, _ -> logins++; M6Session("user", jwt(3000)) }, store) { 1000 }
        val http = object : M6ApiClient() {
            override fun get(url: String, headers: Map<String, String>, cacheMs: Long): String {
                if (url.endsWith("upfront-token")) {
                    tokenRequests++
                    if (tokenRequests == 1) throw M6ApiException(401, "expired")
                    return "{\"token\":\"upfront\"}"
                }
                return """{"clips":[{"assets":[
                    {"type":"usp_dashcenc_h264","video_quality":"unknown","full_physical_path":"https://cdn.test/unknown.mpd"},
                    {"type":"usp_dashcenc_h264","video_quality":"hd","full_physical_path":"https://cdn.test/hd.mpd"},
                    {"type":"subtitle_vtt","full_physical_path":"https://cdn.test/fr.vtt"}
                ]}]}"""
            }
            override fun redirect(url: String) = url
        }
        val playback = M6PlaybackClient(http, sessions).resolve("clip_10")
        assertEquals(1, logins)
        assertEquals(2, tokenRequests)
        assertEquals("https://cdn.test/hd.mpd", playback.manifest)
        assertEquals(listOf("https://cdn.test/fr.vtt"), playback.subtitles)
    }

    @Test fun `missing DASH assets produce an explicit error`() {
        val store = MemorySessionStore().apply { account = M6StoredAccount("email", "pw", M6Session("user", jwt(2000))) }
        val sessions = M6SessionManager(M6AuthGateway { _, _, _ -> error("Unexpected login") }, store) { 1000 }
        val http = object : M6ApiClient() {
            override fun get(url: String, headers: Map<String, String>, cacheMs: Long): String =
                if (url.endsWith("upfront-token")) "{\"token\":\"upfront\"}" else "{\"clips\":[{\"assets\":null}]}"
        }
        val error = assertThrows(IllegalStateException::class.java) { M6PlaybackClient(http, sessions).resolve("clip_10") }
        assertTrue(error.message!!.contains("No compatible DASH Widevine stream"))
    }
}
