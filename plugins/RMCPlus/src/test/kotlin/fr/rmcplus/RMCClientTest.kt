package fr.rmcplus

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RMCClientTest {
    private fun episode(id: Int, season: Int, number: Int) = JSONObject("""{"titre":"Episode $number","background_image":{"url":"https://image.test/$id"},"etiquette":{"saison_episode":"S$season E$number"},"call_to_actions":[{"endpoint":"/page?page_type=player&page_id=$id"}]}""")
    @Test fun `official episode numbers and images are preserved`() {
        val item = RMCClient.item(episode(123, 3, 17))!!
        assertEquals(3, item.season); assertEquals(17, item.episode)
        assertEquals("https://image.test/123", item.poster)
        assertTrue(item.endpoint.endsWith("model=androidtv-ott"))
    }
    @Test fun `episodes are sorted deduplicated and exclude recommendations`() {
        val sections = listOf(
            JSONObject().put("type_tuile", "episode").put("items", org.json.JSONArray(listOf(episode(2, 2, 2), episode(1, 1, 1)))),
            JSONObject().put("type_tuile", "episode").put("items", org.json.JSONArray(listOf(episode(1, 1, 1)))),
            JSONObject().put("type_tuile", "default").put("items", org.json.JSONArray(listOf(episode(9, 9, 9)))))
        val items = RMCClient.episodes(sections)
        assertEquals(listOf(1, 2), items.map { it.season })
    }
    @Test fun `sign-in rejections explain why RMC refused the account`() {
        // Body returned by connect.rmcbfm.com for an unknown account.
        val unknown = RMCClient.signInRejection("""{"__type":"UserNotFoundException","message":"User does not exist."}""")
        assertEquals("UserNotFoundException", unknown)
        assertEquals("No RMC+ account uses this email address.", RMCClient.signInFailure(400, unknown))
        val wrongPassword = RMCClient.signInRejection("""{"__type":"com.amazonaws#NotAuthorizedException","message":"Incorrect username or password."}""")
        assertEquals("Incorrect RMC+ email address or password.", RMCClient.signInFailure(400, wrongPassword))
        val malformed = RMCClient.signInRejection("""{"code":"BAD_REQUEST","errors":[{"field":"clientId","code":"REQUIRED"}]}""")
        assertNull(malformed)
        assertEquals("RMC+ sign-in failed (HTTP 400).", RMCClient.signInFailure(400, malformed))
        assertNull(RMCClient.signInRejection("<html>"))
    }
    @Test fun `authenticated endpoints cannot leave RMC API origin`() {
        listOf("https://evil.test/v1/page", "http://bff.rmcplus.fr/v1/page", "https://bff.rmcplus.fr:444/v1/page", "https://bff.rmcplus.fr/other").forEach {
            assertThrows(IllegalArgumentException::class.java) { RMCClient.endpoint(it) }
        }
    }
    @Test fun `license transport envelope is decoded without changing binary licenses`() {
        assertArrayEquals(byteArrayOf(1, 2, 3), RMCLicenseResponse.decode("{\"license\":\"AQID\"}".toByteArray()))
        assertArrayEquals(byteArrayOf(1, 2, 3), RMCLicenseResponse.decode(byteArrayOf(1, 2, 3)))
        assertThrows(java.io.IOException::class.java) { RMCLicenseResponse.decode("{}".toByteArray()) }
    }
}
