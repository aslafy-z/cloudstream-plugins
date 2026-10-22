package fr.m6plus.auth

import fr.m6plus.api.M6AuthGateway
import fr.m6plus.api.M6Session
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

internal class MemorySessionStore : M6SessionStorage {
    override val deviceId = "test-device"
    var account: M6StoredAccount? = null
    override fun read() = account
    override fun save(account: M6StoredAccount) { this.account = account }
    override fun clear() { account = null }
}

internal fun jwt(expiry: Long) = "header." + Base64.getUrlEncoder().withoutPadding()
    .encodeToString("{\"exp\":$expiry}".toByteArray()) + ".signature"

class M6SessionManagerTest {
    @Test fun `persisted valid token is reused after restart without login`() {
        val store = MemorySessionStore().apply { account = M6StoredAccount("a@b.c", "secret", M6Session("u", jwt(2000))) }
        val manager = M6SessionManager(M6AuthGateway { _, _, _ -> error("Unexpected login") }, store) { 1000 }
        assertEquals("u", manager.session().uid)
    }

    @Test fun `expiry margin and server rejection both renew the token`() {
        val store = MemorySessionStore().apply { account = M6StoredAccount("a@b.c", "secret", M6Session("u", jwt(1050))) }
        var calls = 0
        val manager = M6SessionManager(M6AuthGateway { email, password, device ->
            assertEquals("a@b.c", email); assertEquals("secret", password); assertEquals("test-device", device)
            calls++
            M6Session("u", jwt(2000 + calls.toLong()))
        }, store) { 1000 }
        manager.session()
        manager.session()
        assertEquals(1, calls)
        manager.session(forceRefresh = true)
        assertEquals(2, calls)
    }

    @Test fun `logout removes credentials and prevents silent reconnection`() {
        val store = MemorySessionStore().apply { account = M6StoredAccount("a@b.c", "secret", M6Session("u", jwt(2000))) }
        val manager = M6SessionManager(M6AuthGateway { _, _, _ -> error("Unexpected login") }, store) { 1000 }
        manager.logout()
        assertFalse(manager.isConfigured())
        assertThrows(IllegalStateException::class.java) { manager.session() }
    }

    @Test fun `rejected login does not replace the previous account`() {
        val store = MemorySessionStore().apply { account = M6StoredAccount("old@b.c", "old", M6Session("u", jwt(2000))) }
        val manager = M6SessionManager(M6AuthGateway { _, _, _ -> throw IllegalStateException("refused") }, store) { 1000 }
        assertThrows(IllegalStateException::class.java) { manager.login("new@b.c", "bad") }
        assertEquals("old@b.c", store.account!!.email)
    }
}
