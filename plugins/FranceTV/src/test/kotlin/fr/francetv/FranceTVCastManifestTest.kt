package fr.francetv

import org.junit.Assert.*
import org.junit.Test

internal val castManifest = """<MPD xmlns="urn:mpeg:dash:schema:mpd:2011" xmlns:cenc="urn:mpeg:cenc:2013" mediaPresentationDuration="PT52M3.12S">
  <Period><AdaptationSet><ContentProtection schemeIdUri="urn:mpeg:dash:mp4protection:2011" value="cenc"/>
  <ContentProtection schemeIdUri="urn:uuid:EDEF8BA9-79D6-4ACE-A3C8-27DCD51D21ED"><cenc:pssh>AAECAw==</cenc:pssh></ContentProtection>
  <Representation id="video"><SegmentTemplate media="replay-${'$'}Time${'$'}.dash" initialization="replay-init.dash"/></Representation></AdaptationSet></Period>
</MPD>"""

class FranceTVCastManifestTest {
    @Test fun `manifest advertises license relay and keeps the tokenized CDN path`() {
        val result = FranceTVCastManifest.rewrite(castManifest, "https://cdn.test/token-path/ftv/replay.ism/manifest.mpd?",
            "http://192.0.2.1/session/license").toString(Charsets.UTF_8)
        assertTrue(result.contains("<BaseURL>https://cdn.test/token-path/ftv/replay.ism/</BaseURL>"))
        assertTrue(result.contains("dashif:Laurl"))
        assertTrue(result.contains("licenseUrl=\"http://192.0.2.1/session/license\""))
        assertTrue(result.contains("AAECAw=="))
        assertTrue(result.contains("replay-${'$'}Time${'$'}.dash"))
        assertTrue(result.contains("PT52M3.12S"))
    }

    @Test fun `existing relative BaseURL resolves against the final manifest URL`() {
        val xml = castManifest.replace("<Period>", "<BaseURL>../media/</BaseURL><Period>")
        val result = FranceTVCastManifest.rewrite(xml, "https://cdn.test/video/dash.mpd", "http://192.0.2.1/license").toString(Charsets.UTF_8)
        assertTrue(result.contains("<BaseURL>https://cdn.test/media/</BaseURL>"))
    }

    @Test fun `external entity declarations are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            FranceTVCastManifest.rewrite("<!DOCTYPE MPD [<!ENTITY secret SYSTEM 'file:///etc/passwd'>]>$castManifest", "https://cdn.test/dash.mpd", "http://192.0.2.1/license")
        }
    }

    @Test fun `manifest without Widevine is rejected explicitly`() {
        assertThrows(IllegalArgumentException::class.java) {
            FranceTVCastManifest.rewrite("""<MPD xmlns="urn:mpeg:dash:schema:mpd:2011"/>""", "https://cdn.test/dash.mpd", "http://192.0.2.1/license")
        }
    }
}
