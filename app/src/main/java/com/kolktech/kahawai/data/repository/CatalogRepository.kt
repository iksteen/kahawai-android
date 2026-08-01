package com.kolktech.kahawai.data.repository

import com.kolktech.kahawai.data.network.ApiClient
import com.kolktech.kahawai.data.network.dto.Item
import com.kolktech.kahawai.data.network.dto.ItemDetail
import com.kolktech.kahawai.data.network.dto.ItemsResponse
import com.kolktech.kahawai.data.network.dto.LibrarySummary

class CatalogRepository {
    suspend fun libraries(): List<LibrarySummary> = ApiClient.apiService().libraries().libraries

    suspend fun items(
        library: String? = null,
        q: String? = null,
        sort: String? = null,
        limit: Int? = null,
        offset: Int? = null,
    ): ItemsResponse = ApiClient.apiService().items(library, q, sort, limit, offset)

    suspend fun item(id: String): ItemDetail = ApiClient.apiService().item(id)

    suspend fun children(id: String): List<Item> = ApiClient.apiService().children(id).children

    /// `size` is one of the hub's named sizes ("thumb", "card"); null
    /// serves the original. `version` busts the cache on re-match
    /// (web/src/api.ts:189-195) — the hub caches artwork for a day.
    fun artworkUrl(id: String, version: Long?, size: String? = null): String {
        val params = buildList {
            size?.let { add("size=$it") }
            version?.let { add("v=$it") }
        }
        val query = if (params.isEmpty()) "" else "?${params.joinToString("&")}"
        return "${ApiClient.baseUrl()}api/v1/items/$id/artwork$query"
    }
}
