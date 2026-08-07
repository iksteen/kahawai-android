package com.kolktech.kahawai.data.repository

import com.kolktech.kahawai.data.network.ApiClient
import com.kolktech.kahawai.data.network.ApiService
import com.kolktech.kahawai.data.network.dto.CapabilityProfile
import com.kolktech.kahawai.data.network.dto.Item
import com.kolktech.kahawai.data.network.dto.ItemDetail
import com.kolktech.kahawai.data.network.dto.ItemQueryRequest
import com.kolktech.kahawai.data.network.dto.ItemsResponse
import com.kolktech.kahawai.data.network.dto.LibrarySummary

class CatalogRepository(private val api: ApiService = ApiClient.apiService()) {
    suspend fun libraries(): List<LibrarySummary> = api.libraries().libraries

    suspend fun items(
        library: String? = null,
        q: String? = null,
        sort: String? = null,
        limit: Int? = null,
        offset: Int? = null,
    ): ItemsResponse = api.items(library, q, sort, limit, offset)

    suspend fun item(id: String): ItemDetail = api.item(id)

    /// The item viewer's call: what this client would actually be
    /// served, negotiated against [profile]. Carries the subtitle track
    /// list (with delivery) that used to be a separate `/subtitles`
    /// fetch — see [com.kolktech.kahawai.data.network.ApiService.itemQuery].
    suspend fun queryItem(
        id: String,
        profile: CapabilityProfile,
        audioTrack: Int = 0,
        videoTrack: Int = 0,
        subtitleTrack: Long? = null,
    ): ItemDetail = api.itemQuery(
        id,
        ItemQueryRequest(
            profile = profile,
            audioTrack = audioTrack,
            videoTrack = videoTrack,
            subtitleTrack = subtitleTrack,
        ),
    )

    suspend fun children(id: String): List<Item> = api.children(id).children

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
