package fr.tf1plus

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

internal class TF1HttpException(val status: Int) : Exception("TF1+ HTTP error ($status)")

internal open class TF1Http {
    private data class CacheEntry(val expiresAt: Long, val content: String)
    private val cache = java.util.Collections.synchronizedMap(
        object : LinkedHashMap<String, CacheEntry>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry>?) = size > 64
        }
    )

    open fun get(url: String, headers: Map<String, String> = emptyMap(), cacheMs: Long = 0): String {
        if (cacheMs > 0 && headers.isEmpty()) {
            cache[url]?.takeIf { it.expiresAt > System.currentTimeMillis() }?.let { return it.content }
        }
        val result = request(url, "GET", headers)
        if (cacheMs > 0 && headers.isEmpty()) {
            cache[url] = CacheEntry(System.currentTimeMillis() + cacheMs, result)
        }
        return result
    }

    open fun postForm(url: String, values: Map<String, String>): String =
        request(url, "POST", mapOf("Content-Type" to "application/x-www-form-urlencoded", "Referer" to ROOT),
            values.entries.joinToString("&") { "${enc(it.key)}=${enc(it.value)}" }.toByteArray(Charsets.UTF_8))

    open fun postJson(url: String, value: JSONObject): String =
        request(url, "POST", mapOf("Content-Type" to "application/json", "Referer" to ROOT),
            value.toString().toByteArray(Charsets.UTF_8))

    private fun request(url: String, method: String, headers: Map<String, String>,
                        payload: ByteArray? = null): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 12_000
            readTimeout = 25_000
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/json, */*")
            headers.forEach { (name, value) -> setRequestProperty(name, value) }
        }
        try {
            if (payload != null) {
                conn.doOutput = true
                conn.outputStream.use { it.write(payload) }
            }
            val code = conn.responseCode
            if (code !in 200..299) throw TF1HttpException(code)
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally { conn.disconnect() }
    }

    companion object {
        const val ROOT = "https://www.tf1.fr"
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
        fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
        fun query(url: String, params: Map<String, String>): String =
            url + (if (url.contains('?')) "&" else "?") +
                params.entries.joinToString("&") { "${enc(it.key)}=${enc(it.value)}" }
    }
}

internal fun JSONArray.jsonObjects(): List<JSONObject> =
    (0 until length()).mapNotNull { optJSONObject(it) }

internal fun JSONObject.stringOrNull(key: String): String? =
    optString(key).takeUnless { it.isBlank() || it == "null" }

internal fun JSONObject.objectAt(vararg path: String): JSONObject? {
    var result: JSONObject = this
    for (component in path) result = result.optJSONObject(component) ?: return null
    return result
}
