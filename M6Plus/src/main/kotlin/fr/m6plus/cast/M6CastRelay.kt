package fr.m6plus.cast

import fr.m6plus.diagnostics.M6Diagnostics
import java.io.Closeable
import java.io.DataInputStream
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Local, TV-scoped manifest/license relay. Media segments are fetched directly from M6. */
internal class M6CastRelay(
    private val bindAddress: InetAddress,
    private val peerAddress: InetAddress,
    private val now: () -> Long = System::nanoTime,
    private val ttlNanos: Long = TimeUnit.HOURS.toNanos(6),
    private val onEvent: (M6Diagnostics.Event) -> Unit = {},
) : Closeable {
    private class Session(val manifest: ByteArray, val license: (ByteArray) -> ByteArray, @Volatile var touched: Long)
    private val daemon = ThreadFactory { job -> Thread(job, "M6Cast").apply { isDaemon = true } }
    private val workers = ThreadPoolExecutor(0, 4, 30, TimeUnit.SECONDS, ArrayBlockingQueue(8), daemon)
    private val expiry = Executors.newSingleThreadScheduledExecutor(daemon)
    private val sessions = ConcurrentHashMap<String, Session>()
    private val server = ServerSocket(0, 8, bindAddress)
    val isClosed: Boolean get() = server.isClosed
    private val base = "http://${bindAddress.hostAddress}:${server.localPort}"

    init {
        daemon.newThread {
            while (!server.isClosed) {
                val socket = try { server.accept() } catch (_: IOException) { break }
                try { workers.execute { socket.use(::serve) } }
                catch (_: RejectedExecutionException) { socket.close() }
            }
        }.start()
        expiry.scheduleWithFixedDelay({
            val time = now()
            sessions.entries.removeAll { time - it.value.touched > ttlNanos }
            if (sessions.isEmpty()) close()
        }, 1, 1, TimeUnit.MINUTES)
    }

    @Synchronized fun add(xml: String, origin: String, license: (ByteArray) -> ByteArray): String {
        check(!isClosed) { "The Chromecast session has ended." }
        val id = UUID.randomUUID().toString()
        val manifest = M6CastManifest.rewrite(xml, origin, "$base/$id/license")
        if (sessions.size >= 4) sessions.entries.minByOrNull { it.value.touched }?.let { sessions.remove(it.key) }
        sessions[id] = Session(manifest, license, now())
        return "$base/$id/manifest.mpd"
    }

    private fun serve(socket: Socket) {
        socket.soTimeout = 10_000
        try {
            if (socket.inetAddress != peerAddress && socket.inetAddress != bindAddress) {
                onEvent(M6Diagnostics.Event.PEER_REJECTED)
                reply(socket, 403); return
            }
            val fromTV = socket.inetAddress == peerAddress
            val input = DataInputStream(socket.getInputStream())
            val first = line(input)?.split(' ') ?: return
            if (first.size != 3 || !first[2].startsWith("HTTP/1.")) { reply(socket, 400); return }
            val headers = HashMap<String, String>()
            var size = 0
            while (true) {
                val header = line(input) ?: return
                size += header.length + 2
                if (size > 16_384) { reply(socket, 431); return }
                if (header.isEmpty()) break
                val colon = header.indexOf(':')
                if (colon <= 0) { reply(socket, 400); return }
                val key = header.substring(0, colon).lowercase()
                if (headers.put(key, header.substring(colon + 1).trim()) != null && key == "content-length") {
                    reply(socket, 400); return
                }
            }
            if ("transfer-encoding" in headers) { reply(socket, 400); return }
            val path = first[1].substringBefore('?').split('/')
            val session = path.getOrNull(1)?.let(sessions::get)
            if (path.size != 3 || session == null || now() - session.touched > ttlNanos) { reply(socket, 404); return }
            val resource = path[2]
            if (resource != "manifest.mpd" && resource != "license") { reply(socket, 404); return }
            session.touched = now()
            when {
                first[0] == "OPTIONS" -> reply(socket, 204)
                resource == "manifest.mpd" && first[0] in setOf("GET", "HEAD") -> {
                    if (fromTV) onEvent(M6Diagnostics.Event.MANIFEST)
                    reply(socket, 200, session.manifest, "application/dash+xml", first[0] == "HEAD")
                }
                resource == "license" && first[0] == "POST" -> {
                    val length = headers["content-length"]?.toIntOrNull()
                    if (length == null || length <= 0 || length > 65_536) { reply(socket, 413); return }
                    val challenge = ByteArray(length).also(input::readFully)
                    if (fromTV) onEvent(M6Diagnostics.Event.LICENSE)
                    val license = try { session.license(challenge) } catch (_: Exception) {
                        if (fromTV) onEvent(M6Diagnostics.Event.LICENSE_FAILED)
                        reply(socket, 502); return
                    }
                    reply(socket, 200, license)
                    if (fromTV) onEvent(M6Diagnostics.Event.LICENSE_OK)
                }
                else -> reply(socket, 405)
            }
        } catch (_: Exception) { /* Closed, malformed or timed-out client; never log session data. */ }
    }

    private fun line(input: DataInputStream): String? {
        val bytes = ArrayList<Byte>()
        while (bytes.size < 4096) {
            val next = input.read()
            if (next == -1) return null
            if (next == 10) return bytes.toByteArray().toString(Charsets.US_ASCII).trimEnd('\r')
            bytes.add(next.toByte())
        }
        throw IOException("HTTP header too long")
    }

    private fun reply(socket: Socket, status: Int, body: ByteArray = byteArrayOf(), type: String = "application/octet-stream", head: Boolean = false) {
        val header = "HTTP/1.1 $status Response\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\n" +
            "Access-Control-Allow-Origin: *\r\nAccess-Control-Allow-Methods: GET, HEAD, POST, OPTIONS\r\n" +
            "Access-Control-Allow-Headers: *\r\nAccess-Control-Allow-Private-Network: true\r\n" +
            "Cache-Control: no-store\r\nConnection: close\r\n\r\n"
        socket.getOutputStream().apply { write(header.toByteArray(Charsets.US_ASCII)); if (!head) write(body); flush() }
    }

    override fun close() {
        sessions.clear()
        server.close()
        workers.shutdownNow()
        expiry.shutdownNow()
    }
}
