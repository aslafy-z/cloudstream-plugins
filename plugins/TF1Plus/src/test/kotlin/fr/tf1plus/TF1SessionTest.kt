package fr.tf1plus

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class TF1SessionTest {
    private class Store(var value: TF1Credentials?) : TF1CredentialsStore {
        override fun read() = value
        override fun save(credentials: TF1Credentials) { value = credentials }
        override fun clear() { value = null }
    }
    private class Http : TF1Http() {
        var logins = 0
        var deliveries = 0
        var rejected = false
        override fun get(url: String, headers: Map<String, String>, cacheMs: Long): String {
            if (url.contains("webSdkBootstrap")) return "{}"
            deliveries++
            if (rejected || deliveries == 1) return """{"delivery":{"code":401}}"""
            return """{"delivery":{"url":"https://example.org/video.mpd"}}"""
        }
        override fun postForm(url: String, values: Map<String, String>): String {
            logins++
            return """{"UID":"test","UIDSignature":"signed","signatureTimestamp":123}"""
        }
        override fun postJson(url: String, value: JSONObject): String {
            assertEquals("test", value.getString("UID"))
            assertFalse(value.has("uid"))
            assertEquals(0, value.getJSONArray("consent_ids").length())
            return """{"token":"fresh-token"}"""
        }
    }

    @Test fun restoresSessionWithoutLoginAfterRestart() {
        val store = Store(TF1Credentials("test@example.org", "secret", "cached", 100_000))
        val http = Http()
        assertEquals("cached", TF1Auth(http, store) { 1000 }.token())
        assertEquals("cached", TF1Auth(http, store) { 1000 }.token())
        assertEquals(0, http.logins)
    }

    @Test fun refreshesDeliveryRejectionOnceAndPersistsSession() {
        val store = Store(TF1Credentials("test@example.org", "secret", "old", Long.MAX_VALUE))
        val http = Http()
        val playback = TF1Playback(http, TF1Auth(http, store) { 1000 })
        assertEquals("https://example.org/video.mpd", playback.resolve("video").manifest)
        assertEquals(1, http.logins)
        assertEquals(2, http.deliveries)
        assertEquals("fresh-token", store.value?.token)
    }

    @Test fun doesNotLoopOnRightsRejection() {
        val store = Store(TF1Credentials("test@example.org", "secret", "old", Long.MAX_VALUE))
        val http = Http().apply { rejected = true }
        try {
            TF1Playback(http, TF1Auth(http, store) { 1000 }).resolve("video")
            fail("Expected delivery rejection")
        } catch (error: TF1DeliveryException) { assertEquals(401, error.code) }
        assertEquals(1, http.logins)
        assertEquals(2, http.deliveries)
    }

    @Test fun usesJwtExpiryAndClearsPersistentSessionOnLogout() {
        val claims = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"exp":1234567890}""".toByteArray())
        assertEquals(1234567890000L, TF1AuthPayload.expiry("a.$claims.c", 0))
        val store = Store(TF1Credentials("test@example.org", "secret", "cached", Long.MAX_VALUE))
        val auth = TF1Auth(Http(), store)
        auth.signOut()
        assertFalse(auth.isConfigured())
        assertNull(store.value)
    }
}
