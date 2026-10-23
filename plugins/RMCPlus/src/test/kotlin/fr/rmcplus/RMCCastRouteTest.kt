package fr.rmcplus

import org.junit.Assert.*
import org.junit.Test

class RMCCastRouteTest {
    @Test fun `only Widevine DASH uses the relay`() {
        assertTrue(RMCCastRoute.relayed(RMCStream("https://cdn.test/replay.mpd", true, "synthetic-token")))
        assertFalse(RMCCastRoute.relayed(RMCStream("https://cdn.test/replay.mpd", true, null)))
        assertFalse(RMCCastRoute.relayed(RMCStream("https://cdn.test/replay.m3u8", false, null)))
    }

    @Test fun `Widevine HLS is refused with a clear message`() {
        val stream = RMCStream("https://cdn.test/replay.m3u8", false, "synthetic-token")
        assertFalse(RMCCastRoute.supported(stream))
        val error = assertThrows(IllegalArgumentException::class.java) { RMCCastRoute.relayed(stream) }
        assertTrue(error.message!!.contains("Widevine HLS"))
    }
}

class RMCCastDiagnosticsTest {
    @Test fun `Cast stages are recorded and the last one names failures`() {
        RMCDiagnostics.clear()
        RMCDiagnostics.record(RMCDiagnostics.Cast.FETCH)
        assertEquals("FETCH", RMCDiagnostics.failed())
        val report = RMCDiagnostics.report()
        assertTrue(report.contains("[Cast] Fetch manifest"))
        assertTrue(report.contains("[Cast] Failed at stage FETCH"))
        RMCDiagnostics.clear()
        assertEquals("SESSION", RMCDiagnostics.failed())
    }
}
