package fr.francetv

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

class FranceTVClientTest {
    @Test fun `episodes preserve official numbers and poster preference`() {
        val item = FranceTVClient.item(JSONObject("""{"type":"integrale","si_id":"5aac5411-b566-407f-a73c-16c46b64ce11","episode_title":"Episode","season":6,"episode":1,"images":[{"type":"logo","urls":{"w:800":"logo"}},{"type":"vignette_16x9","urls":{"w:800":"poster"}}]}"""))!!
        assertEquals(6,item.season);assertEquals(1,item.episode);assertEquals("poster",item.poster)
    }
    @Test fun `extracts live and unpublished videos are excluded`() {
        for (extra in listOf("\"type\":\"extrait\"", "\"type\":\"integrale\",\"is_live\":true", "\"type\":\"integrale\",\"is_published\":false")) {
            assertNull(FranceTVClient.item(JSONObject("""{$extra,"title":"Skip","si_id":"5aac5411-b566-407f-a73c-16c46b64ce11"}""")))
        }
    }
    @Test fun `programme seasons exclude recommendations and sort chronologically`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"item":{"type":"program","program_path":"show","label":"Show"},"collections":[
              {"type":"playlist_video","items":[{"type":"integrale","episode_title":"Second","si_id":"5aac5411-b566-407f-a73c-16c46b64ce12","season":2,"episode":1}]},
              {"type":"playlist_video","items":[{"type":"integrale","episode_title":"First","si_id":"5aac5411-b566-407f-a73c-16c46b64ce11","season":1,"episode":1}]},
              {"type":"playlist_mixed","items":[{"type":"integrale","episode_title":"Related","si_id":"5aac5411-b566-407f-a73c-16c46b64ce13"}]}
            ]}"""))
            val detail = FranceTVClient(catalogue=server.url("/").toString()).details("show")
            assertEquals(listOf("First","Second"),detail.episodes.map { it.title })
        }
    }
}
