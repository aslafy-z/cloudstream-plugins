package fr.tf1plus

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class TF1MappingsTest {
    @Test fun programmeMappingIsTolerantOfIncompleteRows() {
        val items = JSONArray("""[
            {"id":"p1","slug":"series-test","name":"Série test",
             "decoration":{"description":"Résumé","image":{"sources":[{"url":"https://example.org/poster.jpg"}]}},
             "categories":[{"id":"series"}]},
            {"id":"p2","name":"Missing slug"},
            {"id":"p3","slug":"missing-title"}
        ]""")
        val result = TF1Mappings.programmes(items)
        assertEquals(1, result.size)
        assertEquals("Série test", result[0].title)
        assertEquals("series-test", result[0].slug)
        assertTrue(result[0].categories.contains("series"))
        assertEquals("https://example.org/poster.jpg", result[0].poster)
    }

    @Test fun videoMappingSupportsEditorialItemsAndSeasonLabels() {
        val items = JSONArray("""[
          {"video":{"id":"v1","decoration":{"label":"Saison 2 - épisode 3","summary":"Test"}}},
          {"id":"v2","decoration":{"label":"Replay du jour"}},
          {"video":{"decoration":{"label":"Missing ID"}}}
        ]""")
        val videos = TF1Mappings.videos(items)
        assertEquals(2, videos.size)
        assertEquals(2, videos[0].season)
        assertEquals(3, videos[0].number)
        assertEquals("Test", videos[0].description)
        assertNull(videos[1].season)
    }
    @Test fun prefersOfficialNumbersAndStillImagesOverPreviewVideos() {
        val videos = TF1Mappings.videos(JSONArray("""[{
            "id":"v1","season":4,"episode":8,
            "decoration":{"label":"Season 2 episode 3","images":[
                {"type":"PREVIEW","sources":[{"url":"https://example.org/preview.mp4"}]},
                {"type":"THUMBNAIL_LARGE","sources":[{"url":"https://example.org/image.avif"},
                    {"url":"https://example.org/image.jpg"}]}]}}]"""))
        assertEquals(4, videos.single().season)
        assertEquals(8, videos.single().number)
        assertEquals("https://example.org/image.jpg", videos.single().poster)
    }
}
