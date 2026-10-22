package fr.m6plus.diagnostics

import fr.m6plus.api.M6ApiException
import fr.m6plus.api.M6AuthGateway
import fr.m6plus.api.M6Session
import fr.m6plus.auth.M6SessionManager
import fr.m6plus.auth.MemorySessionStore
import fr.m6plus.auth.jwt
import java.util.TimeZone
import java.util.concurrent.CancellationException
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class M6DiagnosticsTest {
    @Before fun reset() = M6Diagnostics.clear()
    @After fun cleanup() = M6Diagnostics.clear()

    @Test fun `each entry keeps its timestamp and reports use real line breaks`() {
        var now = 0L
        val buffer = M6DiagnosticBuffer(now = { now }, zone = TimeZone.getTimeZone("UTC"))
        buffer.append("[Auth] Sign-in started")
        now = 1_234L
        buffer.append("[Auth] Sign-in succeeded")
        now = 99_000L
        assertEquals(
            "[1970-01-01 00:00:00.000 +0000] [Auth] Sign-in started\n" +
                "[1970-01-01 00:00:01.234 +0000] [Auth] Sign-in succeeded",
            buffer.snapshot()
        )
    }

    @Test fun `old entries are evicted and clear removes the report`() {
        val buffer = M6DiagnosticBuffer(capacity = 2)
        buffer.append("first")
        buffer.append("second")
        buffer.append("third")
        assertEquals(2, buffer.snapshot().lines().size)
        assertFalse(buffer.snapshot().contains("first"))
        assertTrue(buffer.snapshot().contains("second"))
        buffer.clear()
        assertFalse(buffer.snapshot().contains("third"))
    }

    @Test fun `HTTP errors preserve the exception without copying its sensitive message`() {
        val original = M6ApiException(403, "secret-token https://private.test/account user@example.test")
        val thrown = assertThrows(M6ApiException::class.java) {
            M6Diagnostics.trace(M6Diagnostics.Operation.PLAYBACK) { throw original }
        }
        assertSame(original, thrown)
        val report = M6Diagnostics.report()
        assertTrue(report.contains("[Playback] Resolve replay: failed (HTTP 403)"))
        assertTrue(report.endsWith("\n"))
        assertEquals(3, report.trimEnd().lines().size)
        assertFalse(report.contains("secret-token"))
        assertFalse(report.contains("private.test"))
        assertFalse(report.contains("user@example.test"))
    }

    @Test fun `cancellation is preserved and not reported as failure`() {
        val original = CancellationException("sensitive context")
        assertSame(original, assertThrows(CancellationException::class.java) {
            M6Diagnostics.trace(M6Diagnostics.Operation.SEARCH) { throw original }
        })
        assertTrue(M6Diagnostics.snapshot().contains("cancelled"))
        assertFalse(M6Diagnostics.snapshot().contains("failed"))
        assertFalse(M6Diagnostics.snapshot().contains("sensitive context"))
    }

    @Test fun `successful listing records counts without content or identifiers`() {
        val items = listOf("private programme title", "private identifier")
        assertSame(items, M6Diagnostics.trace(M6Diagnostics.Operation.PROGRAMS) { items })
        val report = M6Diagnostics.snapshot()
        assertTrue(report.contains("Load programmes: succeeded (2 results)"))
        assertFalse(report.contains("private"))
    }

    @Test fun `real session manager records login reuse and logout without credentials`() {
        val store = MemorySessionStore()
        val manager = M6SessionManager(M6AuthGateway { _, _, _ -> M6Session("private-user-id", jwt(2000)) }, store) { 1000 }
        manager.login("private@example.test", "private-password")
        manager.session()
        manager.logout()
        val report = M6Diagnostics.snapshot()
        assertTrue(report.contains("Account sign-in: succeeded"))
        assertTrue(report.contains("Valid session reused"))
        assertTrue(report.contains("Account sign-out: succeeded"))
        assertFalse(report.contains("private"))
        assertFalse(report.contains(jwt(2000)))
    }
}
