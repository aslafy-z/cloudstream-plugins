package fr.tv5mondeplus

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class TV5ClientTest {
    @Test fun `gateway variables preserve required whitespace and escape identifiers`() {
        assertEquals("{\"contentId\": \"redbee:test:fr\"}", TV5Client.variables("redbee:test:fr"))
    }

    @Test fun `catalogue prefers French metadata and poster images`() {
        val value = JSONObject("""{"assetId":"test_1","type":"TV_SHOW","localized":[{"locale":"en","title":"English"},{"locale":"fr","title":"Français","images":[{"type":"cover","url":"landscape"},{"type":"poster","url":"portrait"}]}]}""")
        val item = TV5Client.asset(value)!!
        assertEquals("Français", item.title); assertEquals("portrait", item.poster); assertTrue(item.series)
        value.put("live", true); assertNull(TV5Client.asset(value))
    }
    @Test fun `content uses provider identifier and official episode number`() {
        val value = JSONObject("""{"title":"Episode","episodeNumber":12,"externalIds":[{"scheme":"OTHER","identifier":"wrong"},{"scheme":"DATA_PROVIDER","identifier":"correct_1"}]}""")
        val item = TV5Client.content(value, 3)!!
        assertEquals("correct_1", item.id); assertEquals(3, item.season); assertEquals(12, item.episode)
    }
    @Test fun `playback selects Widevine DASH instead of the first format`() {
        val value = JSONObject("""{"formats":[{"format":"HLS","mediaLocator":"https://cdn.test/fairplay.m3u8","drm":{"com.apple.fps":{}}},{"format":"DASH","mediaLocator":"https://cdn.test/stream.mpd","drm":{"com.widevine.alpha":{"licenseServerUrl":"https://license.test/wv"}}}]}""")
        val stream = TV5Client.selectStream(value)
        assertEquals("https://cdn.test/stream.mpd", stream.url); assertEquals("https://license.test/wv", stream.license)
        assertThrows(java.io.IOException::class.java) { TV5Client.selectStream(JSONObject("{\"formats\":[]}")) }
    }
}
