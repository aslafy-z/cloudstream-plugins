package fr.m6plus.api

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class M6SearchClientTest {
    private fun search(content: String) = M6SearchClient(object : M6ApiClient() {
        override fun postJson(url: String, body: JSONObject, headers: Map<String, String>): String =
            """{"results":[{"hits":[{"content":{"id":"42"},"item":{"itemContent":$content}}]}]}"""
    }).search("programme").single()

    @Test fun `search result includes the Algolia program poster`() {
        val program = search("""{"title":"Programme","image":{"id":"123"},"secondaryImage":{"id":"456"},"description":"Résumé"}""")
        assertEquals("42", program.id)
        assertEquals("https://images.6play.fr/v1/images/123/raw", program.poster)
        assertEquals("Résumé", program.description)
    }

    @Test fun `secondary image is used when primary image is missing`() {
        val program = search("""{"title":"Programme","image":null,"secondaryImage":{"id":"456"}}""")
        assertEquals("https://images.6play.fr/v1/images/456/raw", program.poster)
    }

    @Test fun `program remains searchable without an image`() {
        val program = search("""{"title":"Programme","image":{"id":null}}""")
        assertEquals("Programme", program.title)
        assertNull(program.poster)
    }
}
