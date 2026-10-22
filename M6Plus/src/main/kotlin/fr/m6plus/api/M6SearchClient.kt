package fr.m6plus.api

import fr.m6plus.diagnostics.M6Diagnostics
import fr.m6plus.model.M6Program
import org.json.JSONArray
import org.json.JSONObject

internal class M6SearchClient(private val http: M6ApiClient) {
    fun search(query: String): List<M6Program> = M6Diagnostics.trace(M6Diagnostics.Operation.SEARCH) {
        if (query.isBlank()) return@trace emptyList()
        val request = JSONObject().put("requests", JSONArray().put(JSONObject()
            .put("indexName", "rtlmutu_prod_bedrock_layout_items_v2_m6web_main")
            .put("query", query)
            .put("params", "hitsPerPage=50&facetFilters=[[\"metadata.item_type:program\"],[\"metadata.platforms_assets:m6group_web\"]]")))
        val raw = http.postJson("https://nhacvivxxk-dsn.algolia.net/1/indexes/*/queries", request,
            mapOf("x-algolia-api-key" to "6ef59fc6d78ac129339ab9c35edd41fa",
                "x-algolia-application-id" to "NHACVIVXXK"))
        return@trace JSONObject(raw).optJSONArray("results")?.objects().orEmpty()
            .flatMap { it.optJSONArray("hits")?.objects().orEmpty() }
            .mapNotNull { hit ->
                val id = hit.optJSONObject("content")?.text("id") ?: return@mapNotNull null
                val content = hit.optJSONObject("item")?.optJSONObject("itemContent")
                    ?: return@mapNotNull null
                val title = content.text("title") ?: return@mapNotNull null
                val imageId = content.optJSONObject("image")?.text("id")
                    ?: content.optJSONObject("secondaryImage")?.text("id")
                val poster = imageId?.let { "https://images.6play.fr/v1/images/${M6ApiClient.encode(it)}/raw" }
                M6Program(id, title, poster, content.text("description"))
            }.distinctBy { it.id }
    }
}
