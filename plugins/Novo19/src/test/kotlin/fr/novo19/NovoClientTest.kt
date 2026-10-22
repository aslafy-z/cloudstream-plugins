package fr.novo19

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class NovoClientTest {
    @Test fun `episode labels supply official season and episode numbers`() {
        val item = NovoClient.item(JSONObject("""{"id":"OF-123_565BFFb","type":"EPISODE","title":"Episode","subtitle":"S2E6","href":"/player/test","thumbnail":{"src":"https://image.test/poster"}}"""))!!
        assertEquals(2, item.season); assertEquals(6, item.episode)
        assertEquals("https://image.test/poster", item.poster)
    }
    @Test fun `season queries exclude recommendation rails`() {
        val queries = JSONObject().put("bffTileContents(/sections/s1/tiles)", JSONObject().put("data", JSONObject().put("name", "season")))
            .put("bffTileContents(/sections/reco/tiles)", JSONObject().put("data", JSONObject().put("name", "recommendations")))
        val data = JSONObject().put("initialState", JSONObject().put("api", JSONObject().put("queries", queries)))
        assertEquals(listOf("season"), NovoClient.queries(data, "s1").map { it.getString("name") })
    }
    @Test fun `catalogue links cannot leave the official site`() {
        assertThrows(IllegalArgumentException::class.java) { NovoClient.address("https://evil.test/details/foo") }
        assertThrows(IllegalArgumentException::class.java) { NovoClient.address("http://novo19.ouest-france.fr/details/foo") }
    }
}
