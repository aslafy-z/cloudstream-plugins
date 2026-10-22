package fr.m6plus.cast

import fr.m6plus.diagnostics.M6Diagnostics
import fr.m6plus.api.M6ApiClient
import fr.m6plus.api.objects
import fr.m6plus.api.text
import fr.m6plus.auth.M6SessionManager
import org.json.JSONArray
import org.json.JSONObject

/** Native receiver data is kept in memory and sent only to the selected Cast device. */
internal class M6NativeCastRequest(private val videoId: String, private val user: JSONObject, val profiles: List<JSONObject>) {
    fun payload(profile: JSONObject): JSONObject = JSONObject()
        .put("idVideo", videoId).put("type", "video").put("sectionCode", "main")
        .put("user", user).put("profile", profile)
        .put("gdpr", JSONObject().put("device", JSONObject()).put("user", JSONObject()).put("tcfv2", JSONObject.NULL))
        .put("sender", JSONObject().put("platform", "m6group_web").put("appVersion", "6.53.0"))
        .put("preferences", JSONObject().put("audio", JSONObject.NULL).put("subtitle", JSONObject.NULL))
        .put("service", "6play").put("override", JSONObject())
}

internal class M6NativeCastClient(private val http: M6ApiClient, private val sessions: M6SessionManager) {
    fun prepare(videoId: String): M6NativeCastRequest = M6Diagnostics.trace(M6Diagnostics.Operation.NATIVE_PREPARE) {
        require(Regex("clip_\\d+").matches(videoId)) { "Invalid M6+ replay identifier." }
        // The encrypted store deliberately persists only UID/JWT, not the Gigya signature.
        val identity = sessions.session(forceRefresh = true)
        val signature = identity.signature ?: throw IllegalStateException("Incomplete M6+ session.")
        val timestamp = identity.signatureTimestamp ?: throw IllegalStateException("Incomplete M6+ session.")
        val headers = mapOf("X-Customer-Name" to "m6web", "X-Client-Release" to "6.53.0",
            "x-auth-device-id" to sessions.deviceId, "x-auth-gigya-uid" to identity.uid,
            "x-auth-gigya-signature" to signature, "x-auth-gigya-signature-timestamp" to timestamp.toString())
        val url = "https://6play-users.6play.fr/v2/platforms/m6group_web/users/${M6ApiClient.encode(identity.uid)}/profiles"
        val profiles = JSONArray(http.get(url, headers)).objects().filter { it.text("uid") != null }
        check(profiles.isNotEmpty()) { "No M6+ profiles are available. Create a profile on the M6+ website." }
        val user = JSONObject().put("UID", identity.uid).put("UIDSignature", signature)
            .put("signatureTimestamp", timestamp).put("userOffers", "")
        return@trace M6NativeCastRequest(videoId, user, profiles)
    }
}
