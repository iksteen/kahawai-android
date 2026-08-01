package com.kolktech.kahawai.data.network

import com.kolktech.kahawai.data.network.dto.BootstrapResponse
import com.kolktech.kahawai.data.network.dto.ChildrenResponse
import com.kolktech.kahawai.data.network.dto.ItemDetail
import com.kolktech.kahawai.data.network.dto.ItemsResponse
import com.kolktech.kahawai.data.network.dto.LibrariesResponse
import com.kolktech.kahawai.data.network.dto.LoginRequest
import com.kolktech.kahawai.data.network.dto.ProgressRequest
import com.kolktech.kahawai.data.network.dto.RefreshRequest
import com.kolktech.kahawai.data.network.dto.FontsResponse
import com.kolktech.kahawai.data.network.dto.SeekRequest
import com.kolktech.kahawai.data.network.dto.SeekResponse
import com.kolktech.kahawai.data.network.dto.StartSessionRequest
import com.kolktech.kahawai.data.network.dto.StartSessionResponse
import com.kolktech.kahawai.data.network.dto.SubtitlesResponse
import com.kolktech.kahawai.data.network.dto.TokenPair
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
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

    @GET("api/v1/libraries")
    suspend fun libraries(): LibrariesResponse

    @GET("api/v1/items")
    suspend fun items(
        @Query("library") library: String? = null,
        @Query("q") q: String? = null,
        @Query("sort") sort: String? = null,
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null,
    ): ItemsResponse

    @GET("api/v1/items/{id}")
    suspend fun item(@Path("id") id: String): ItemDetail

    @GET("api/v1/items/{id}/children")
    suspend fun children(@Path("id") id: String): ChildrenResponse

    /// Track list + computed delivery for THIS client (tracks.rs
    /// delivery()); `.vtt`/`.ass` bodies and the session-scoped
    /// `subs-{id}.{ass,jsonl}` taps are raw text/binary/streaming, fetched
    /// with plain OkHttp against [com.kolktech.kahawai.data.network.ApiClient.authenticatedOkHttpClient]
    /// rather than through Retrofit.
    @GET("api/v1/items/{id}/subtitles")
    suspend fun subtitles(
        @Path("id") id: String,
        @Query("ass_render") assRender: Boolean,
        @Query("graphics_overlay") graphicsOverlay: Boolean,
    ): SubtitlesResponse

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
}
