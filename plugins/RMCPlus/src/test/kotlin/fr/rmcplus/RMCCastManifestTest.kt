package fr.rmcplus

import org.junit.Assert.*
import org.junit.Test

internal val castManifest = """<MPD xmlns="urn:mpeg:dash:schema:mpd:2011" xmlns:cenc="urn:mpeg:cenc:2013" mediaPresentationDuration="PT44M27.28S">
  <Period><AdaptationSet><ContentProtection schemeIdUri="urn:uuid:edef8ba9-79d6-4ace-a3c8-27dcd51d21ed"><cenc:pssh>AAECAw==</cenc:pssh></ContentProtection>
  <Representation id="video"><SegmentTemplate media="track-${'$'}Number${'$'}.m4s" initialization="init.mp4"/></Representation></AdaptationSet></Period>
</MPD>"""

class RMCCastManifestTest {
    @Test fun `manifest advertises license relay and preserves encrypted media references`() {
        val result = RMCCastManifest.rewrite(castManifest, "https://cdn.test/video/dash.mpd?token=cdn", "http://192.0.2.1/session/license").toString(Charsets.UTF_8)
        assertTrue(result.contains("<BaseURL>https://cdn.test/video/</BaseURL>"))
        assertTrue(result.contains("http://192.0.2.1/session/license"))
        assertTrue(result.contains("AAECAw=="))
        assertTrue(result.contains("track-${'$'}Number${'$'}.m4s"))
        assertTrue(result.contains("PT44M27.28S"))
        assertFalse(result.contains("?token=cdn"))
    }

    @Test fun `existing relative BaseURL resolves against the final manifest URL`() {
        val xml = castManifest.replace("<Period>", "<BaseURL>../media/</BaseURL><Period>")
        val result = RMCCastManifest.rewrite(xml, "https://cdn.test/video/dash.mpd", "http://192.0.2.1/license").toString(Charsets.UTF_8)
        assertTrue(result.contains("<BaseURL>https://cdn.test/media/</BaseURL>"))
    }

    @Test fun `external entity declarations are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            RMCCastManifest.rewrite("<!DOCTYPE MPD [<!ENTITY secret SYSTEM 'file:///etc/passwd'>]>$castManifest", "https://cdn.test/dash.mpd", "http://192.0.2.1/license")
        }
    }

    @Test fun `manifest without Widevine is rejected explicitly`() {
        assertThrows(IllegalArgumentException::class.java) {
            RMCCastManifest.rewrite("""<MPD xmlns="urn:mpeg:dash:schema:mpd:2011"/>""", "https://cdn.test/dash.mpd", "http://192.0.2.1/license")
        }
    }
}
