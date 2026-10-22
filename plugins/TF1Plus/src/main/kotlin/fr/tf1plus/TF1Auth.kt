package fr.tf1plus

import okio.ByteString.Companion.decodeBase64
import org.json.JSONArray
import org.json.JSONObject

internal data class TF1Credentials(val email: String, val password: String,
    val token: String? = null, val expiresAt: Long = 0)
internal data class TF1Identity(val uid: String, val signature: String, val timestamp: Long)
internal class TF1AuthException(val code: Int) : IllegalStateException(when (code) {
    403042 -> "TF1+ rejected the email address or password."
    206001 -> "Complete your account registration on tf1.fr before signing in."
    else -> "TF1+ sign-in failed (code $code). Check your account on tf1.fr."
})

internal object TF1AuthPayload {
    fun check(json: JSONObject) {
        val code = json.optInt("errorCode", 0)
        if (code != 0) throw TF1AuthException(code)
    }

    fun parseLogin(json: JSONObject): TF1Identity {
        check(json)
        val user = json.optJSONObject("userInfo") ?: json
        return TF1Identity(
            user.stringOrNull("UID") ?: throw IllegalStateException("Missing TF1+ user identifier."),
            user.stringOrNull("UIDSignature") ?: throw IllegalStateException("Missing TF1+ user signature."),
            user.optLong("signatureTimestamp").takeIf { it > 0 }
                ?: throw IllegalStateException("Missing TF1+ signature timestamp.")
        )
    }

    fun exchange(identity: TF1Identity): JSONObject = JSONObject()
        .put("UID", identity.uid).put("signature", identity.signature).put("timestamp", identity.timestamp)
        .put("consent_ids", JSONArray())

    fun expiry(token: String, now: Long): Long = runCatching {
        val claims = token.split('.')[1].decodeBase64()?.utf8() ?: return@runCatching null
        JSONObject(claims).optLong("exp").takeIf { it > 0 }?.times(1000)
    }.getOrNull() ?: (now + 15 * 60_000L)
}

internal class TF1Auth(private val http: TF1Http, private val store: TF1CredentialsStore,
    private val now: () -> Long = System::currentTimeMillis) {
    private var current: TF1Credentials? = null

    @Synchronized fun isConfigured(): Boolean = store.read() != null

    @Synchronized fun signIn(email: String, password: String) = TF1Diagnostics.trace(TF1Diagnostics.Operation.LOGIN) {
        val account = authenticate(TF1Credentials(email.trim(), password))
        store.save(account)
        current = account
    }

    @Synchronized fun token(refresh: Boolean = false): String = TF1Diagnostics.trace(TF1Diagnostics.Operation.SESSION) {
        val account = current ?: store.read() ?: run {
            TF1Diagnostics.record(TF1Diagnostics.Event.ACCOUNT_MISSING)
            throw IllegalStateException("Sign in to TF1+ in the plugin settings.")
        }
        if (!refresh && account.token != null && account.expiresAt > now() + 60_000L) {
            current = account
            TF1Diagnostics.record(TF1Diagnostics.Event.TOKEN_REUSED)
            return@trace account.token
        }
        TF1Diagnostics.trace(TF1Diagnostics.Operation.REFRESH) {
            val renewed = authenticate(account)
            store.save(renewed)
            current = renewed
            checkNotNull(renewed.token)
        }
    }

    @Synchronized fun clearMemory() {
        current = null
        http.clearSessionCookies()
    }

    @Synchronized fun signOut() = TF1Diagnostics.trace(TF1Diagnostics.Operation.LOGOUT) {
        clearMemory()
        store.clear()
    }

    private fun authenticate(credentials: TF1Credentials): TF1Credentials {
        TF1Diagnostics.trace(TF1Diagnostics.Operation.BOOTSTRAP) {
            TF1AuthPayload.check(JSONObject(http.get(TF1Http.query("https://compte.tf1.fr/accounts.webSdkBootstrap", mapOf(
                "apiKey" to GIGYA_KEY, "pageURL" to TF1Http.ROOT, "sdk" to "js_latest",
                "sdkBuild" to "13987", "format" to "json")))))
        }
        val identity = TF1Diagnostics.trace(TF1Diagnostics.Operation.GIGYA) {
            TF1AuthPayload.parseLogin(JSONObject(http.postForm("https://compte.tf1.fr/accounts.login", mapOf(
                "loginID" to credentials.email, "password" to credentials.password,
                "sessionExpiration" to "31536000", "targetEnv" to "jssdk",
                "include" to "identities-all,data,profile,preferences", "includeUserInfo" to "true",
                "loginMode" to "standard", "lang" to "fr", "APIKey" to GIGYA_KEY,
                "sdk" to "js_latest", "authMode" to "cookie", "pageURL" to TF1Http.ROOT,
                "sdkBuild" to "13987", "format" to "json"
            ))))
        }
        val token = TF1Diagnostics.trace(TF1Diagnostics.Operation.TOKEN) {
            JSONObject(http.postJson("${TF1Http.ROOT}/token/gigya/web", TF1AuthPayload.exchange(identity)))
                .stringOrNull("token") ?: throw IllegalStateException("TF1+ login did not return a playback token.")
        }
        return credentials.copy(token = token, expiresAt = TF1AuthPayload.expiry(token, now()))
    }

    companion object {
        // Public client identifier advertised by TF1's web login page.
        private const val GIGYA_KEY = "3_hWgJdARhz_7l1oOp3a8BDLoR9cuWZpUaKG4aqF7gum9_iK3uTZ2VlDBl8ANf8FVk"
    }
}
