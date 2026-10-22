package fr.m6plus.cast

import fr.m6plus.api.M6ApiClient
import fr.m6plus.api.M6AuthGateway
import fr.m6plus.api.M6Session
import fr.m6plus.auth.M6SessionManager
import fr.m6plus.auth.M6StoredAccount
import fr.m6plus.auth.MemorySessionStore
import fr.m6plus.auth.jwt
import org.junit.Assert.*
import org.junit.Test

class M6NativeCastClientTest {
    @Test fun `native Cast refreshes identity and includes a selected profile without exposing JWT`() {
        var logins = 0
        val store = MemorySessionStore().apply { account = M6StoredAccount("email", "pw", M6Session("user", jwt(2000))) }
        val sessions = M6SessionManager(M6AuthGateway { _, _, _ ->
            logins++; M6Session("user", jwt(3000), "signature", 123)
        }, store) { 1000 }
        val http = object : M6ApiClient() {
            override fun get(url: String, headers: Map<String, String>, cacheMs: Long): String {
                assertTrue(url.endsWith("/users/user/profiles"))
                assertEquals("signature", headers["x-auth-gigya-signature"])
                return """[{"uid":"profile-1","username":"Profil"}]"""
            }
        }
        val request = M6NativeCastClient(http, sessions).prepare("clip_42")
        val payload = request.payload(request.profiles.single())
        assertEquals(1, logins)
        assertEquals("clip_42", payload.getString("idVideo"))
        assertEquals("profile-1", payload.getJSONObject("profile").getString("uid"))
        assertEquals("", payload.getJSONObject("user").getString("userOffers"))
        assertEquals(123, payload.getJSONObject("user").getLong("signatureTimestamp"))
        assertTrue(payload.getJSONObject("gdpr").isNull("tcfv2"))
        assertFalse(payload.toString().contains(jwt(3000)))
    }

    @Test fun `account without a profile fails before launching the native receiver`() {
        val store = MemorySessionStore().apply { account = M6StoredAccount("email", "pw", M6Session("user", jwt(2000))) }
        val sessions = M6SessionManager(M6AuthGateway { _, _, _ -> M6Session("user", jwt(3000), "signature", 123) }, store) { 1000 }
        val http = object : M6ApiClient() {
            override fun get(url: String, headers: Map<String, String>, cacheMs: Long) = "[]"
        }
        val error = assertThrows(IllegalStateException::class.java) { M6NativeCastClient(http, sessions).prepare("clip_42") }
        assertTrue(error.message!!.contains("profil"))
    }
}
