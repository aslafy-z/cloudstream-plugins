package fr.m6plus.api

import org.json.JSONObject

internal class M6Session(val uid: String, val jwt: String,
    val signature: String? = null, val signatureTimestamp: Long? = null)

internal fun interface M6AuthGateway {
    fun login(email: String, password: String, deviceId: String): M6Session
}

internal class M6AuthClient(private val http: M6ApiClient) : M6AuthGateway {
    override fun login(email: String, password: String, deviceId: String): M6Session {
        val raw = http.postForm(
            "https://login-gigya.m6.fr/accounts.login",
            mapOf("loginID" to email, "password" to password, "apiKey" to GIGYA_KEY,
                "format" to "json", "targetEnv" to "mobile"),
            mapOf("Referer" to "https://www.6play.fr/connexion")
        )
        val trimmed = raw.trim()
        val payload = if (trimmed.startsWith("{")) trimmed else {
            Regex("""^[A-Za-z_$][\w.$]*\((.*)\);?$""", RegexOption.DOT_MATCHES_ALL)
                .matchEntire(trimmed)?.groupValues?.get(1)
                ?: throw IllegalStateException("Invalid Gigya response.")
        }
        val gigya = JSONObject(payload)
        if (gigya.optInt("errorCode", 0) != 0) throw IllegalStateException("M6+ credentials rejected.")
        val uid = gigya.text("UID") ?: throw IllegalStateException("M6+ credentials rejected.")
        val signature = gigya.text("UIDSignature") ?: throw IllegalStateException("Incomplete Gigya response.")
        val timestamp = gigya.text("signatureTimestamp") ?: throw IllegalStateException("Incomplete Gigya response.")
        val jwt = JSONObject(http.get("https://front-auth.6cloud.fr/v2/platforms/m6group_web/getJwt",
            mapOf("x-auth-gigya-signature" to signature,
                "x-auth-gigya-signature-timestamp" to timestamp,
                "x-auth-gigya-uid" to uid,
                "x-auth-device-id" to deviceId,
                "x-customer-name" to "m6web"))).text("token")
            ?: throw IllegalStateException("Missing M6+ token.")
        return M6Session(uid, jwt, signature, timestamp.toLongOrNull())
    }

    companion object {
        // Public client identifier used by the M6+ web login flow.
        private const val GIGYA_KEY = "3_hH5KBv25qZTd_sURpixbQW6a4OsiIzIEF2Ei_2H7TXTGLJb_1Hr4THKZianCQhWK"
    }
}
