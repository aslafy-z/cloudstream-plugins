package fr.m6plus.auth

import fr.m6plus.api.M6AuthGateway
import fr.m6plus.api.M6Session
import fr.m6plus.diagnostics.M6Diagnostics
import okio.ByteString.Companion.decodeBase64
import org.json.JSONObject

internal class M6SessionManager(
    private val auth: M6AuthGateway,
    private val store: M6SessionStorage,
    private val nowSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    val deviceId: String get() = store.deviceId
    @Synchronized fun login(email: String, password: String) = M6Diagnostics.trace(M6Diagnostics.Operation.LOGIN) {
        val session = auth.login(email.trim(), password, store.deviceId)
        store.save(M6StoredAccount(email.trim(), password, session))
    }

    @Synchronized fun session(forceRefresh: Boolean = false): M6Session = M6Diagnostics.trace(M6Diagnostics.Operation.SESSION) {
        val account = store.read() ?: run {
            M6Diagnostics.record(M6Diagnostics.Event.ACCOUNT_MISSING)
            throw IllegalStateException("Sign in to M6+ in the plugin settings.")
        }
        if (!forceRefresh && valid(account.session.jwt)) {
            M6Diagnostics.record(M6Diagnostics.Event.SESSION_REUSED)
            return@trace account.session
        }
        M6Diagnostics.trace(M6Diagnostics.Operation.REFRESH) {
            val renewed = auth.login(account.email, account.password, store.deviceId)
            store.save(M6StoredAccount(account.email, account.password, renewed))
            renewed
        }
    }

    @Synchronized fun isConfigured(): Boolean = store.read() != null
    @Synchronized fun logout() = M6Diagnostics.trace(M6Diagnostics.Operation.LOGOUT) { store.clear() }

    private fun valid(jwt: String): Boolean {
        return try {
            val payload = jwt.split('.')[1].decodeBase64()?.utf8() ?: return false
            JSONObject(payload).optLong("exp") > nowSeconds() + 60
        } catch (_: Exception) { false }
    }
}
