package fr.t18

import org.json.JSONArray
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class T18ClientTest {
    @Test fun `cards use lazy loaded posters and exclude unrelated links`() {
        val html = """<a class="product-card" href="/prog/test-123"><img src="data:image/gif;base64,x" data-src="https://resize.t18.fr/poster.jpg" alt="Title"><h3 class="product-card__title">Programme</h3></a><a class="product-card" href="/cat/test"><img alt="Category"></a>"""
        val item = T18Client.cards(Jsoup.parse(html)).single()
        assertEquals("Programme", item.title); assertEquals("https://resize.t18.fr/poster.jpg", item.poster)
    }
    @Test fun `replays are deduplicated and ordered by broadcast date`() {
        val items = T18Client.replays(JSONArray("""[{"url":"/prog/test/new","title":"New","broadcastedAt":"2026-10-22"},{"url":"/prog/test/old","title":"Old","broadcastedAt":"2026-10-21"},{"url":"/prog/test/new","title":"New"}]"""))
        assertEquals(listOf("Old", "New"), items.map { it.title })
    }
    @Test fun `Dailymotion identifier is validated and catalogue origin is restricted`() {
        assertEquals("k123abc", T18Client.videoId("""<div data-dailymotion-video-id="k123abc"></div>"""))
        assertNull(T18Client.videoId("""<div data-dailymotion-video-id="../unsafe"></div>"""))
        assertThrows(IllegalArgumentException::class.java) { T18Client.address("https://evil.test/prog/test") }
    }
}
