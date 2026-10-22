package fr.m6plus.api

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

internal class M6ApiException(val status: Int, message: String) : Exception(message)

internal class M6ApiClient {
    private data class Cached(val until: Long, val value: String)
    private val cache = ConcurrentHashMap<String, Cached>()

    fun get(url: String, headers: Map<String, String> = emptyMap(), cacheMs: Long = 0): String {
        if (cacheMs > 0) cache[url]?.takeIf { it.until > System.currentTimeMillis() }?.let { return it.value }
        val value = request(url, "GET", headers)
        if (cacheMs > 0) cache[url] = Cached(System.currentTimeMillis() + cacheMs, value)
        return value
    }

    fun postForm(url: String, fields: Map<String, String>, headers: Map<String, String> = emptyMap()): String =
        request(url, "POST", headers + ("Content-Type" to "application/x-www-form-urlencoded"),
            fields.entries.joinToString("&") { "${encode(it.key)}=${encode(it.value)}" }.toByteArray(StandardCharsets.UTF_8))

    fun postJson(url: String, body: JSONObject, headers: Map<String, String> = emptyMap()): String =
        request(url, "POST", headers + ("Content-Type" to "application/json"),
            body.toString().toByteArray(StandardCharsets.UTF_8))

    fun redirect(url: String): String {
        val connection = open(url, "GET", emptyMap())
        connection.instanceFollowRedirects = false
        return try {
            val code = connection.responseCode
            if (code in 300..399) URL(URL(url), connection.getHeaderField("Location") ?: return url).toString()
            else if (code in 200..299) url
            else throw M6ApiException(code, "Le manifeste M6+ est indisponible ($code).")
        } finally { connection.disconnect() }
    }

    private fun request(url: String, method: String, headers: Map<String, String>, body: ByteArray? = null): String {
        val connection = open(url, method, headers)
        return try {
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.use { it.write(body) }
            }
            val code = connection.responseCode
            if (code !in 200..299) throw M6ApiException(code, when (code) {
                401, 403 -> "Connexion ou droits M6+ invalides ($code)."
                404 -> "Ce programme M6+ n'est plus disponible."
                else -> "Erreur du service M6+ ($code)."
            })
            connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        } finally { connection.disconnect() }
    }

    private fun open(url: String, method: String, headers: Map<String, String>): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 12_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android TV) AppleWebKit/537.36 Chrome/120.0 Safari/537.36")
            setRequestProperty("Accept", "application/json, */*")
            headers.forEach { (key, value) -> setRequestProperty(key, value) }
        }

    companion object {
        fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
    }
}

internal fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
internal fun JSONObject.text(key: String): String? = optString(key).takeUnless { it.isBlank() || it == "null" }
