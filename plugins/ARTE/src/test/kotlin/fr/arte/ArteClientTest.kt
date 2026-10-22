package fr.arte

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class ArteClientTest {
    @Test fun `items preserve episode numbers and omit unavailable replays and navigation links`() {
        val items = ArteClient.items(JSONObject("""{"data":[
          {"programId":"123456-002-A","title":"Series","subtitle":"Part 2","episodeInfo":{"season":3,"episode":2},"mainImage":{"url":"https://img.test/__SIZE__"}},
          {"programId":"RC-012345","title":"Collection"},
          {"programId":"123456-003-A","title":"Future","availability":{"hasVideoStreams":false}},
          {"title":"Category","deeplink":"arte://emac/SER"}
        ]}"""))
        assertEquals(2, items.size)
        assertEquals("Series - Part 2", items[0].title)
        assertEquals(3, items[0].season)
        assertEquals(2, items[0].episode)
        assertEquals("https://img.test/940x530", items[0].poster)
        assertTrue(items[1].collection)
    }

    @Test fun `player keeps one master with all audio versions and rejects empty streams`() {
        val result = ArteClient.streams(JSONObject("""{"data":{"attributes":{"streams":[
          {"url":"https://cdn.test/master.m3u8","versions":[{"label":"French"},{"label":"German"}],"mainQuality":{"label":"720p"}},
          {"url":"http://cdn.test/insecure.m3u8"},
          {"url":"https://cdn.test/master.m3u8"}
        ]}}}"""))
        assertEquals(1, result.size)
        assertEquals("French / German", result.single().label)
        assertThrows(IOException::class.java) { ArteClient.streams(JSONObject("""{"data":{"attributes":{"streams":[]}}}""")) }
    }

    @Test fun `collection pagination excludes recommendations and deduplicates video ids`() {
        MockWebServer().use { server ->
            server.start()
            val next = server.url("/next")
            server.enqueue(MockResponse().setBody("""{"metadata":{"title":"Series"},"zones":[
              {"code":"collection_subcollection_RC-012345_A","content":{"data":[{"programId":"123456-001-A","title":"One"}],"pagination":{"links":{"next":"$next"}}}},
              {"code":"collection_associated_RC-012345","content":{"data":[{"programId":"999999-001-A","title":"Unrelated"}]}}
            ]}"""))
            server.enqueue(MockResponse().setBody("""{"data":[{"programId":"123456-001-A","title":"One"},{"programId":"123456-002-A","title":"Two"}]}"""))
            val details = ArteClient(base = server.url("/").toString()).details("RC-012345")
            assertEquals(listOf("123456-001-A", "123456-002-A"), details.episodes.map { it.id })
            assertNotNull(server.takeRequest().getHeader("Authorization"))
            assertEquals("/next", server.takeRequest().path)
        }
    }

    @Test fun `primary video list takes precedence over overlapping editorial subcollections`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"zones":[
              {"code":"collection_videos_RC-012345","content":{"data":[{"programId":"123456-001-A","title":"One"}]}},
              {"code":"collection_subcollection_RC-012345_OLD","content":{"data":[{"programId":"999999-001-A","title":"Archive"}]}}
            ]}"""))
            assertEquals(listOf("123456-001-A"), ArteClient(base = server.url("/").toString()).details("RC-012345").episodes.map { it.id })
        }
    }

    @Test fun `player request never carries the catalogue bearer token`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"data":{"attributes":{"streams":[{"url":"https://cdn.test/master.m3u8"}]}}}"""))
            ArteClient(base = server.url("/").toString()).playback("123456-001-A")
            assertNull(server.takeRequest().getHeader("Authorization"))
        }
    }

    @Test fun `pagination cannot send the catalogue token to another origin`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"zones":[{"code":"collection_videos_RC-012345","content":{"data":[],"pagination":{"links":{"next":"https://other.test/page"}}}}]}"""))
            assertThrows(IllegalArgumentException::class.java) { ArteClient(base = server.url("/").toString()).details("RC-012345") }
            assertEquals(1, server.requestCount)
        }
    }
}
