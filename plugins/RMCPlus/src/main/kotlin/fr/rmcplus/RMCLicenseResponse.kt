package fr.rmcplus

import okio.ByteString.Companion.decodeBase64
import org.json.JSONObject
import java.io.IOException

internal object RMCLicenseResponse {
    fun decode(bytes: ByteArray): ByteArray {
        val payload = bytes.toString(Charsets.UTF_8).trim()
        if (!payload.startsWith("{")) return bytes
        val encoded = try { JSONObject(payload).text("license") } catch (_: Exception) { null }
        return encoded?.decodeBase64()?.toByteArray() ?: throw IOException("Invalid RMC+ license response.")
    }
}
