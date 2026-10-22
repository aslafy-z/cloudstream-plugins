package fr.m6plus.api

import org.json.JSONObject

internal data class M6Session(val uid: String, val jwt: String)

internal class M6AuthClient(private val http: M6ApiClient) {
    fun login(email: String, password: String, deviceId: String): M6Session {
        val raw = http.postForm(
            "https://login-gigya.m6.fr/accounts.login",
            mapOf("loginID" to email, "password" to password, "apiKey" to GIGYA_KEY,
                "format" to "json", "targetEnv" to "mobile"),
            mapOf("Referer" to "https://www.6play.fr/connexion")
        )
        val gigya = JSONObject(raw.removePrefix("jsonp_3bbusffr388pem4(").removeSuffix(");"))
        val uid = gigya.text("UID") ?: throw IllegalStateException("Identifiants M6+ refusés.")
        val signature = gigya.text("UIDSignature") ?: throw IllegalStateException("Réponse Gigya incomplète.")
        val timestamp = gigya.text("signatureTimestamp") ?: throw IllegalStateException("Réponse Gigya incomplète.")
        val jwt = JSONObject(http.get("https://front-auth.6cloud.fr/v2/platforms/m6group_web/getJwt",
            mapOf("x-auth-gigya-signature" to signature,
                "x-auth-gigya-signature-timestamp" to timestamp,
                "x-auth-gigya-uid" to uid,
                "x-auth-device-id" to deviceId,
                "x-customer-name" to "m6web"))).text("token")
            ?: throw IllegalStateException("Jeton M6+ absent.")
        return M6Session(uid, jwt)
    }

    companion object {
        // Public client identifier used by the M6+ web login flow.
        private const val GIGYA_KEY = "3_hH5KBv25qZTd_sURpixbQW6a4OsiIzIEF2Ei_2H7TXTGLJb_1Hr4THKZianCQhWK"
    }
}
