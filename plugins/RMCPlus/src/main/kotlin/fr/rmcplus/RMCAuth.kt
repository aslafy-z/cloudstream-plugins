package fr.rmcplus

import org.json.JSONObject
import okio.ByteString.Companion.decodeBase64

data class RMCCredentials(val email: String, val password: String, val token: String?, val expiresAt: Long)
internal class RMCAuth(private val client: RMCClient, private val store: RMCCredentialsStore) {
    @Synchronized fun isConfigured() = store.read() != null
    @Synchronized fun signIn(email: String, password: String) = RMCDiagnostics.trace("Account sign-in") {
        val token = client.login(email, password)
        val expiration = runCatching {
            JSONObject(token.split('.')[1].decodeBase64()!!.utf8()).getLong("exp") * 1000
        }.getOrDefault(System.currentTimeMillis() + 300_000)
        store.save(RMCCredentials(email, password, token, expiration))
    }
    @Synchronized fun token(refresh: Boolean = false): String {
        val account = store.read() ?: throw RMCException(401, "Sign in to RMC+ in the plugin settings first.")
        if (refresh || account.token == null || account.expiresAt < System.currentTimeMillis() + 60_000)
            signIn(account.email, account.password)
        return store.read()?.token ?: throw RMCException(401, "Please sign in to RMC+ again.")
    }
    @Synchronized fun signOut() { store.clear(); RMCDiagnostics.record("Account signed out") }
}
