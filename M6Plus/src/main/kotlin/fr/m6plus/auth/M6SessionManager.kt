package fr.m6plus.auth

import android.util.Base64
import fr.m6plus.api.M6AuthClient
import fr.m6plus.api.M6Session
import fr.m6plus.settings.M6SettingsStore
import org.json.JSONObject

internal class M6SessionManager(private val auth: M6AuthClient, private val store: M6SettingsStore) {
    @Synchronized fun login(email: String, password: String) {
        val session = auth.login(email.trim(), password, store.deviceId)
        store.put("email", email.trim())
        store.put("password", password)
        save(session)
    }

    @Synchronized fun session(forceRefresh: Boolean = false): M6Session {
        val uid = store.get("uid")
        val jwt = store.get("jwt")
        if (!forceRefresh && uid != null && jwt != null && valid(jwt)) return M6Session(uid, jwt)
        val email = store.get("email") ?: throw IllegalStateException("Connectez-vous à M6+ dans les paramètres du plugin.")
        val password = store.get("password") ?: throw IllegalStateException("Connectez-vous à M6+ dans les paramètres du plugin.")
        return auth.login(email, password, store.deviceId).also(::save)
    }

    fun isConfigured(): Boolean = store.get("email") != null
    fun logout() = store.clearSession()

    private fun save(session: M6Session) {
        store.put("uid", session.uid)
        store.put("jwt", session.jwt)
    }

    private fun valid(jwt: String): Boolean = try {
        val payload = String(Base64.decode(jwt.split('.')[1], Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8)
        JSONObject(payload).optLong("exp") > System.currentTimeMillis() / 1000 + 60
    } catch (_: Exception) { false }
}
