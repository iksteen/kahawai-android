package com.kolktech.kahawai.data.network

import com.kolktech.kahawai.data.network.dto.BootstrapResponse
import com.kolktech.kahawai.data.network.dto.ChildrenResponse
import com.kolktech.kahawai.data.network.dto.ItemDetail
import com.kolktech.kahawai.data.network.dto.ItemQueryRequest
import com.kolktech.kahawai.data.network.dto.ItemsResponse
import com.kolktech.kahawai.data.network.dto.LibrariesResponse
import com.kolktech.kahawai.data.network.dto.LoginRequest
import com.kolktech.kahawai.data.network.dto.LogoutRequest
import com.kolktech.kahawai.data.network.dto.OkResponse
import com.kolktech.kahawai.data.network.dto.PrefsResponse
import com.kolktech.kahawai.data.network.dto.ProgressRequest
import com.kolktech.kahawai.data.network.dto.ProviderConfiguration
import com.kolktech.kahawai.data.network.dto.PutPrefRequest
import com.kolktech.kahawai.data.network.dto.PutPrefResponse
import com.kolktech.kahawai.data.network.dto.RefreshRequest
import com.kolktech.kahawai.data.network.dto.SetOpenSubtitlesAccountRequest
import com.kolktech.kahawai.data.network.dto.FontsResponse
import com.kolktech.kahawai.data.network.dto.SeekRequest
import com.kolktech.kahawai.data.network.dto.SeekResponse
import com.kolktech.kahawai.data.network.dto.StartSessionRequest
import com.kolktech.kahawai.data.network.dto.StartSessionResponse
import com.kolktech.kahawai.data.network.dto.TokenPair
import com.kolktech.kahawai.data.network.dto.UpdatedResponse
import com.kolktech.kahawai.data.network.dto.WatchedRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/// The kahawai hub's client-facing /api/v1/* surface (HUB-11..18).
/// Grows alongside the app's own build order; see
/// crates/kahawai-hub/src/api.rs for the authoritative route table.
interface ApiService {
    @GET("api/v1/bootstrap")
    suspend fun bootstrap(): BootstrapResponse

    @POST("api/v1/auth/token")
    suspend fun login(@Body body: LoginRequest): TokenPair

    @POST("api/v1/auth/refresh")
    suspend fun refresh(@Body body: RefreshRequest): TokenPair

    /// Revokes this login's refresh family server-side. Authenticated
    /// (needs the access bearer) — called via [ApiClient.apiService].
    @POST("api/v1/auth/logout")
    suspend fun logout(@Body body: LogoutRequest)

    @GET("api/v1/libraries")
    suspend fun libraries(): LibrariesResponse

    @GET("api/v1/items")
    suspend fun items(
        @Query("library") library: String? = null,
        @Query("q") q: String? = null,
        @Query("sort") sort: String? = null,
        /// Started and not finished, most recently watched first — the
        /// home screen's continue-watching row (api.rs:2213-2216). Its
        /// own order, so `sort`/`q` do not apply; `library` still scopes
        /// it.
        @Query("in_progress") inProgress: Boolean? = null,
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null,
    ): ItemsResponse

    /// "What did we find" — sources without stream info, no negotiation.
    @GET("api/v1/items/{id}")
    suspend fun item(@Path("id") id: String): ItemDetail

    /// "What would I be served" (RFC 10008 QUERY, api.rs `item_query`) —
    /// same response shape as [item] plus `negotiated`: the source
    /// negotiation chose, its per-stream verdicts, and the unified
    /// subtitle track list with delivery computed for the declared
    /// [com.kolktech.kahawai.data.network.dto.CapabilityProfile]. This
    /// replaced the deleted `GET /items/{id}/subtitles` listing route —
    /// one request answers both halves of the item page.
    @HTTP(method = "QUERY", path = "api/v1/items/{id}", hasBody = true)
    suspend fun itemQuery(@Path("id") id: String, @Body body: ItemQueryRequest): ItemDetail

    /// Ticks an item watched/unwatched without a playback session
    /// (HUB-10, api.rs `item_set_watched`). Either direction clears the
    /// item's resume position server-side.
    @PUT("api/v1/items/{id}/watched")
    suspend fun setWatched(@Path("id") id: String, @Body body: WatchedRequest): UpdatedResponse

    @GET("api/v1/items/{id}/children")
    suspend fun children(@Path("id") id: String): ChildrenResponse

    @GET("api/v1/items/{id}/fonts")
    suspend fun fonts(@Path("id") id: String): FontsResponse

    @POST("api/v1/playback/sessions")
    suspend fun startSession(@Body body: StartSessionRequest): StartSessionResponse

    @POST("api/v1/playback/sessions/{id}/seek")
    suspend fun seek(@Path("id") id: String, @Body body: SeekRequest): SeekResponse

    @POST("api/v1/playback/sessions/{id}/progress")
    suspend fun progress(@Path("id") id: String, @Body body: ProgressRequest)

    @DELETE("api/v1/playback/sessions/{id}")
    suspend fun endSession(@Path("id") id: String)

    /// Per-user preferences (HUB-33) — audio/subtitle language defaults,
    /// OpenSubtitles account, bandwidth cap, ASS fallback order. See
    /// [com.kolktech.kahawai.ui.settings.ServerSettingsScreen].
    @GET("api/v1/prefs")
    suspend fun prefs(): PrefsResponse

    @PUT("api/v1/prefs")
    suspend fun putPref(@Body body: PutPrefRequest): PutPrefResponse

    /// Per-viewer OpenSubtitles account (HUB-21), sealed in the hub's own
    /// credential store (kahawai commit 7835630, "Seal viewer OpenSubtitles
    /// accounts"). Replaced the old `opensubtitles.username`/`.password`
    /// generic prefs, which the hub no longer reads at all.
    @GET("api/v1/account/opensubtitles")
    suspend fun openSubtitlesAccount(): ProviderConfiguration

    /// Both fields required; empty either side is a 400. Replaces whatever
    /// account was previously attached.
    @POST("api/v1/account/opensubtitles")
    suspend fun setOpenSubtitlesAccount(@Body body: SetOpenSubtitlesAccountRequest): OkResponse

    @DELETE("api/v1/account/opensubtitles")
    suspend fun deleteOpenSubtitlesAccount(): OkResponse
}
