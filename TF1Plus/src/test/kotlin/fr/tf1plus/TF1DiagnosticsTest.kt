package fr.tf1plus

import java.util.TimeZone
import java.util.concurrent.CancellationException
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TF1DiagnosticsTest {
    @Before fun setup() = TF1Diagnostics.clear()
    @After fun teardown() = TF1Diagnostics.clear()

    @Test fun timestampsAndNewlines() {
        var time = 0L
        val buffer = TF1DiagnosticBuffer(now = { time }, zone = TimeZone.getTimeZone("UTC"))
        buffer.append("[Playback] Replay link requested")
        time = 1234L
        buffer.append("[Playback] DASH manifest resolved")
        assertEquals(
            "[1970-01-01 00:00:00.000 +0000] [Playback] Replay link requested\n" +
                "[1970-01-01 00:00:01.234 +0000] [Playback] DASH manifest resolved",
            buffer.snapshot()
        )
    }

    @Test fun boundedBufferAndClear() {
        val buffer = TF1DiagnosticBuffer(capacity = 2)
        buffer.append("first")
        buffer.append("second")
        buffer.append("third")
        assertFalse(buffer.snapshot().contains("first"))
        assertEquals(2, buffer.snapshot().lines().size)
        buffer.clear()
        assertEquals("No operations recorded in this session.", buffer.snapshot())
    }

    @Test fun httpStatusIsCapturedButExceptionTextIsNot() {
        val secret = TF1HttpException(403)
        val thrown = assertThrows(TF1HttpException::class.java) {
            TF1Diagnostics.trace(TF1Diagnostics.Operation.PLAYBACK) { throw secret }
        }
        assertSame(secret, thrown)
        val report = TF1Diagnostics.report()
        assertTrue(report.contains("[Playback] Resolve replay: failed (HTTP 403)"))
        assertTrue(report.endsWith("\n"))
    }

    @Test fun arbitrarySensitiveExceptionMessagesNeverEnterReport() {
        assertThrows(IllegalStateException::class.java) {
            TF1Diagnostics.trace(TF1Diagnostics.Operation.LOGIN) {
                throw IllegalStateException("password=secret uid=my-id https://private.example/api")
            }
        }
        val report = TF1Diagnostics.report()
        assertTrue(report.contains("Account sign-in: failed"))
        assertFalse(report.contains("secret"))
        assertFalse(report.contains("my-id"))
        assertFalse(report.contains("private.example"))
    }

    @Test fun cancellationIsNotReportedAsFailure() {
        val cancelled = CancellationException("private details")
        assertSame(cancelled, assertThrows(CancellationException::class.java) {
            TF1Diagnostics.trace(TF1Diagnostics.Operation.SEARCH_VIDEOS) { throw cancelled }
        })
        assertTrue(TF1Diagnostics.snapshot().contains("cancelled"))
        assertFalse(TF1Diagnostics.snapshot().contains("private details"))
    }

    @Test fun recordCountDoesNotLogCatalogueContents() {
        val input = listOf("secret episode name", "token")
        assertSame(input, TF1Diagnostics.trace(TF1Diagnostics.Operation.EPISODES) { input })
        assertTrue(TF1Diagnostics.snapshot().contains("succeeded (2 results)"))
        assertFalse(TF1Diagnostics.snapshot().contains("secret episode name"))
    }

    @Test fun gigyaCodeIsShownWithoutResponseContents() {
        assertThrows(TF1AuthException::class.java) {
            TF1Diagnostics.trace(TF1Diagnostics.Operation.GIGYA) {
                throw TF1AuthException(403042)
            }
        }
        assertTrue(TF1Diagnostics.snapshot().contains("Gigya error 403042"))
    }
}
