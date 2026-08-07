package com.kolktech.kahawai.data.repository

import com.kolktech.kahawai.data.network.ApiClient
import com.kolktech.kahawai.data.network.ApiService
import com.kolktech.kahawai.data.network.dto.CapabilityProfile
import com.kolktech.kahawai.data.network.dto.Item
import com.kolktech.kahawai.data.network.dto.ItemDetail
import com.kolktech.kahawai.data.network.dto.ItemQueryRequest
import com.kolktech.kahawai.data.network.dto.ItemsResponse
import com.kolktech.kahawai.data.network.dto.LibrarySummary

/// [api] is built lazily, not eagerly at construction: this repository is
/// `remember`ed once at the top of the nav graph, which composes (and
/// thus constructs this) before the user has necessarily been through
/// Setup — an eager [ApiClient.apiService] call would hit
/// [ApiClient.baseUrl]'s "hub server not configured yet" throw on every
/// first launch, well before the Setup screen ever renders. Deferring
/// construction to first actual use means it only runs once a hub is
/// configured, since every suspend fun below is only reachable from
/// screens gated behind Setup.
class CatalogRepository(apiProvider: () -> ApiService = { ApiClient.apiService() }) {
    private val api: ApiService by lazy(apiProvider)

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
    /// Called from composition (Coil's `model`), so it must not throw:
    /// null falls through to Coil as "no image" rather than crashing the
    /// screen that's still on-screen while the server gets unconfigured
    /// (e.g. mid "change server" navigation).
    fun artworkUrl(id: String, version: Long?, size: String? = null): String? {
        val base = ApiClient.baseUrlOrNull() ?: return null
        val params = buildList {
            size?.let { add("size=$it") }
            version?.let { add("v=$it") }
        }
        val query = if (params.isEmpty()) "" else "?${params.joinToString("&")}"
        return "${base}api/v1/items/$id/artwork$query"
    }
}
