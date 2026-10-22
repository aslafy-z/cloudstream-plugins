package fr.tf1plus

import org.json.JSONArray
import org.json.JSONObject

internal data class TF1Credentials(val email: String, val password: String)
internal data class TF1Identity(val uid: String, val signature: String, val timestamp: Long)
internal class TF1AuthException(val code: Int) : IllegalStateException("TF1+ Gigya error ($code)")

internal object TF1AuthPayload {
    fun parseLogin(json: JSONObject): TF1Identity {
        if (json.optInt("errorCode", 0) != 0) throw TF1AuthException(json.optInt("errorCode"))
        val user = json.optJSONObject("userInfo") ?: json
        return TF1Identity(
            user.stringOrNull("UID") ?: throw IllegalStateException("Missing TF1+ user identifier."),
            user.stringOrNull("UIDSignature") ?: throw IllegalStateException("Missing TF1+ user signature."),
            user.optLong("signatureTimestamp").takeIf { it > 0 }
                ?: throw IllegalStateException("Missing TF1+ signature timestamp.")
        )
    }
}

internal class TF1Auth(private val http: TF1Http, private val store: TF1CredentialsStore) {
    private var cachedToken: String? = null
    private var tokenExpiryMs: Long = 0

    @Synchronized fun isConfigured(): Boolean = store.read() != null

    @Synchronized fun signIn(email: String, password: String) = TF1Diagnostics.trace(TF1Diagnostics.Operation.LOGIN) {
        val value = getNewToken(TF1Credentials(email.trim(), password))
        store.save(TF1Credentials(email.trim(), password))
        cachedToken = value
        tokenExpiryMs = System.currentTimeMillis() + 15 * 60_000L
    }

    @Synchronized fun token(refresh: Boolean = false): String =
        TF1Diagnostics.trace(TF1Diagnostics.Operation.SESSION) {
            if (!refresh && System.currentTimeMillis() < tokenExpiryMs) cachedToken?.let {
                TF1Diagnostics.record(TF1Diagnostics.Event.TOKEN_REUSED)
                return@trace it
            }
            val credentials = store.read() ?: run {
                TF1Diagnostics.record(TF1Diagnostics.Event.ACCOUNT_MISSING)
                throw IllegalStateException("Sign in to TF1+ in plugin settings.")
            }
            TF1Diagnostics.trace(TF1Diagnostics.Operation.REFRESH) {
                getNewToken(credentials).also {
                    cachedToken = it
                    tokenExpiryMs = System.currentTimeMillis() + 15 * 60_000L
                }
            }
        }

    @Synchronized fun clearMemory() {
        cachedToken = null
        tokenExpiryMs = 0
        http.clearSessionCookies()
    }

    @Synchronized fun signOut() = TF1Diagnostics.trace(TF1Diagnostics.Operation.LOGOUT) {
        clearMemory()
        store.clear()
    }

    private fun getNewToken(credentials: TF1Credentials): String {
        TF1Diagnostics.trace(TF1Diagnostics.Operation.BOOTSTRAP) {
            http.get(TF1Http.query("https://compte.tf1.fr/accounts.webSdkBootstrap", mapOf(
                "apiKey" to GIGYA_KEY, "pageURL" to TF1Http.ROOT, "sd" to "js_latest",
                "sdkBuild" to "13987", "format" to "json")))
        }
        val response = TF1Diagnostics.trace(TF1Diagnostics.Operation.GIGYA) {
            http.postForm("https://compte.tf1.fr/accounts.login", mapOf(
            "loginID" to credentials.email,
            "password" to credentials.password,
            "sessionExpiration" to "31536000",
            "targetEnv" to "jssdk",
            "include" to "identities-all,data,profile,preferences,",
            "includeUserInfo" to "true",
            "loginMode" to "standard",
            "lang" to "fr",
            "APIKey" to GIGYA_KEY,
            "sdk" to "js_latest",
            "authMode" to "cookie",
            "pageURL" to TF1Http.ROOT,
            "sdkBuild" to "13987",
            "format" to "json"
        ))
        }
        val identity = TF1Diagnostics.trace(TF1Diagnostics.Operation.GIGYA) {
            TF1AuthPayload.parseLogin(JSONObject(response))
        }
        val consent = JSONArray()
        listOf(1, 2, 3, 4, 10001, 10003, 10005, 10007, 10013, 10015, 10017,
            10019, 10009, 10011, 13002, 13001, 10004, 10014, 10016, 10018,
            10020, 10010, 10012, 10006, 10008).forEach { consent.put(it.toString()) }
        val token = TF1Diagnostics.trace(TF1Diagnostics.Operation.TOKEN) {
            JSONObject(http.postJson("https://www.tf1.fr/token/gigya/web",
            JSONObject().put("uid", identity.uid).put("signature", identity.signature)
                .put("timestamp", identity.timestamp).put("consent_ids", consent)))
            .stringOrNull("token") ?: throw IllegalStateException("TF1+ login did not return a playback token.")
        }
        return token
    }

    companion object {
        // Public Gigya WEB client identifier (not a credential).
        private const val GIGYA_KEY = "3_hWgJdARhz_7l1oOp3a8BDLoR9cuWZpUaKG4aqF7gum9_iK3uTZ2VlDBl8ANf8FVk"
    }
}
