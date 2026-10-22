package fr.m6plus.api

import fr.m6plus.model.M6Program
import org.json.JSONArray
import org.json.JSONObject

internal class M6SearchClient(private val http: M6ApiClient) {
    fun search(query: String): List<M6Program> {
        if (query.isBlank()) return emptyList()
        val request = JSONObject().put("requests", JSONArray().put(JSONObject()
            .put("indexName", "rtlmutu_prod_bedrock_layout_items_v2_m6web_main")
            .put("query", query)
            .put("params", "hitsPerPage=50&facetFilters=[[\"metadata.item_type:program\"],[\"metadata.platforms_assets:m6group_web\"]]")))
        val raw = http.postJson("https://nhacvivxxk-dsn.algolia.net/1/indexes/*/queries", request,
            mapOf("x-algolia-api-key" to "6ef59fc6d78ac129339ab9c35edd41fa",
                "x-algolia-application-id" to "NHACVIVXXK"))
        return JSONObject(raw).optJSONArray("results")?.objects().orEmpty()
            .flatMap { it.optJSONArray("hits")?.objects().orEmpty() }
            .mapNotNull { hit ->
                val id = hit.optJSONObject("content")?.text("id") ?: return@mapNotNull null
                val title = hit.optJSONObject("item")?.optJSONObject("itemContent")?.text("title")
                    ?: return@mapNotNull null
                M6Program(id, title, null, null)
            }.distinctBy { it.id }
    }
}
