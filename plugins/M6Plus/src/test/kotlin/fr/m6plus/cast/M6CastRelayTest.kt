package fr.m6plus.cast

import fr.m6plus.diagnostics.M6Diagnostics

import org.junit.Assert.*
import org.junit.Test
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.Socket
import java.net.URL

class M6CastRelayTest {
    private val localhost = InetAddress.getByName("127.0.0.1")
    private fun request(url: String, method: String = "GET", body: ByteArray? = null): Pair<Int, ByteArray> {
        val http = URL(url).openConnection() as HttpURLConnection
        http.connectTimeout = 2000
        http.readTimeout = 2000
        http.requestMethod = method
        if (body != null) { http.doOutput = true; http.outputStream.use { it.write(body) } }
        return try {
            val code = http.responseCode
            code to ((if (code < 400) http.inputStream else http.errorStream)?.use { it.readBytes() } ?: byteArrayOf())
        } finally { http.disconnect() }
    }

    @Test fun `TV fetches modified manifest and receives opaque binary license`() {
        val challenge = byteArrayOf(0, 1, -1, 42)
        val license = byteArrayOf(-1, 0, 12)
        var calls = 0
        val events = java.util.concurrent.CopyOnWriteArrayList<M6Diagnostics.Event>()
        val done = java.util.concurrent.CountDownLatch(1)
        val relay = M6CastRelay(localhost, localhost, onEvent = {
            events += it
            if (it == M6Diagnostics.Event.LICENSE_OK) done.countDown()
        })
        relay.use {
            val url = relay.add(castManifest, "https://cdn.test/dash.mpd") { incoming ->
                assertArrayEquals(challenge, incoming); calls++; license
            }
            val (status, manifest) = request(url)
            assertEquals(200, status)
            assertTrue(manifest.toString(Charsets.UTF_8).contains(url.replace("manifest.mpd", "license")))
            val (licenseStatus, received) = request(url.replace("manifest.mpd", "license"), "POST", challenge)
            assertEquals(200, licenseStatus)
            assertArrayEquals(license, received)
            assertEquals(1, calls)
            assertTrue(done.await(2, java.util.concurrent.TimeUnit.SECONDS))
            assertEquals(listOf(M6Diagnostics.Event.MANIFEST, M6Diagnostics.Event.LICENSE, M6Diagnostics.Event.LICENSE_OK), events)
            assertEquals(404, request(url.replace("manifest.mpd", "missing"), "POST", challenge).first)
            assertEquals(1, calls)
            assertEquals(204, request(url.replace("manifest.mpd", "license"), "OPTIONS").first)
        }
        assertTrue(relay.isClosed)
    }

    @Test fun `license failure is observable without exposing upstream secrets`() {
        val events = java.util.concurrent.CopyOnWriteArrayList<M6Diagnostics.Event>()
        M6CastRelay(localhost, localhost, onEvent = { events += it }).use { relay ->
            val url = relay.add(castManifest, "https://cdn.test/dash.mpd") { error("sensitive upstream token") }
            val (status, body) = request(url.replace("manifest.mpd", "license"), "POST", byteArrayOf(1))
            assertEquals(502, status)
            assertTrue(body.isEmpty())
            assertEquals(listOf(M6Diagnostics.Event.LICENSE, M6Diagnostics.Event.LICENSE_FAILED), events)
        }
    }

    @Test fun `other network peers cannot use relay`() {
        M6CastRelay(localhost, InetAddress.getByName("127.0.0.2")).use { relay ->
            val url = URL(relay.add(castManifest, "https://cdn.test/dash.mpd") { error("Unexpected license request") })
            Socket().use { socket ->
                socket.bind(java.net.InetSocketAddress(InetAddress.getByName("127.0.0.3"), 0))
                socket.connect(java.net.InetSocketAddress(localhost, url.port))
                socket.soTimeout = 2000
                assertTrue(socket.getInputStream().bufferedReader().readLine().startsWith("HTTP/1.1 403"))
            }
        }
    }

    @Test fun `expired sessions cannot request licenses`() {
        var time = 0L
        M6CastRelay(localhost, localhost, { time }, 10).use { relay ->
            val url = relay.add(castManifest, "https://cdn.test/dash.mpd") { error("Unexpected license request") }
            time = 11
            assertEquals(404, request(url.replace("manifest.mpd", "license"), "POST", byteArrayOf(1)).first)
        }
    }

    @Test fun `oversized license requests are rejected before reading a body`() {
        M6CastRelay(localhost, localhost).use { relay ->
            val url = URL(relay.add(castManifest, "https://cdn.test/dash.mpd") { error("Unexpected license request") })
            Socket(localhost, url.port).use { socket ->
                socket.soTimeout = 2000
                socket.getOutputStream().write("POST ${url.path.replace("manifest.mpd", "license")} HTTP/1.1\r\nHost: localhost\r\nContent-Length: 65537\r\n\r\n".toByteArray())
                assertTrue(socket.getInputStream().bufferedReader().readLine().startsWith("HTTP/1.1 413"))
            }
        }
    }
}
