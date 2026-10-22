package fr.m6plus.api

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class M6CatalogClientTest {
    @Test fun `middleware page cap does not truncate program list`() {
        val offsets = mutableListOf<Int>()
        val http = object : M6ApiClient() {
            override fun get(url: String, headers: Map<String, String>, cacheMs: Long): String {
                val offset = Regex("offset=(\\d+)").find(url)!!.groupValues[1].toInt()
                offsets += offset
                val count = if (offset == 0) 100 else 2
                return JSONArray((offset until offset + count).map { JSONObject().put("id", it).put("title", "Program $it") }).toString()
            }
        }
        val programs = M6CatalogClient(http).programs("120")
        assertEquals(102, programs.size)
        assertEquals(listOf(0, 100), offsets)
        assertEquals("101", programs.last().id)
    }

    @Test fun `playlist clips remain separate and inherit real season numbers`() {
        val http = object : M6ApiClient() {
            override fun get(url: String, headers: Map<String, String>, cacheMs: Long) = """
                [{"id":"playlist_1","type":"playlist","title":"Emission","clips":[
                  {"video_id":"clip_10","title":"Partie 1","product":{"season":17}},
                  {"video_id":"clip_11","title":"Partie 2","product":{"season":17}}
                ]}]
            """.trimIndent()
        }
        val episodes = M6CatalogClient(http).episodes("872", emptyList())
        assertEquals(listOf("clip_10", "clip_11"), episodes.map { it.id })
        assertEquals(listOf(17, 17), episodes.map { it.season })
    }

    @Test fun `repeating pages fail clearly instead of looping forever`() {
        val page = JSONArray((0 until 100).map { JSONObject().put("id", it).put("title", "Program $it") }).toString()
        val http = object : M6ApiClient() {
            override fun get(url: String, headers: Map<String, String>, cacheMs: Long) = page
        }
        assertThrows(IllegalStateException::class.java) { M6CatalogClient(http).programs("120") }
    }
}
