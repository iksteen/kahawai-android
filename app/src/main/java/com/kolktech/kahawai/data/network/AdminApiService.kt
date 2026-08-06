package com.kolktech.kahawai.data.network

import com.kolktech.kahawai.data.network.dto.AdminLibrariesResponse
import com.kolktech.kahawai.data.network.dto.ApproveRequest
import com.kolktech.kahawai.data.network.dto.ApproveResponse
import com.kolktech.kahawai.data.network.dto.AttachCollectionRequest
import com.kolktech.kahawai.data.network.dto.CollectionsResponse
import com.kolktech.kahawai.data.network.dto.CreateLibraryRequest
import com.kolktech.kahawai.data.network.dto.CreateLibraryResponse
import com.kolktech.kahawai.data.network.dto.EnrichRunResponse
import com.kolktech.kahawai.data.network.dto.EnrichStatusResponse
import com.kolktech.kahawai.data.network.dto.EnrollmentsResponse
import com.kolktech.kahawai.data.network.dto.ProvidersResponse
import com.kolktech.kahawai.data.network.dto.RefreshLibraryResponse
import com.kolktech.kahawai.data.network.dto.SavedResponse
import com.kolktech.kahawai.data.network.dto.SatellitesResponse
import com.kolktech.kahawai.data.network.dto.SessionsResponse
import com.kolktech.kahawai.data.network.dto.SetAnidbRequest
import com.kolktech.kahawai.data.network.dto.SetAnidbResponse
import com.kolktech.kahawai.data.network.dto.SetChainRequest
import com.kolktech.kahawai.data.network.dto.SetDisabledRequest
import com.kolktech.kahawai.data.network.dto.SetTmdbRequest
import com.kolktech.kahawai.data.network.dto.SetTvdbRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/// The hub's admin-only `/admin/v1/*` surface (crates/kahawai-hub/src/api.rs:109-176) —
/// requires the JWT `admin` claim; the hub 403s otherwise regardless of
/// what this client shows/hides. See [ApiService] for the client-facing
/// `/api/v1/*` surface this sits alongside.
interface AdminApiService {
    @GET("admin/v1/enrollments")
    suspend fun enrollments(): EnrollmentsResponse

    @POST("admin/v1/enrollments/approve")
    suspend fun approve(@Body body: ApproveRequest): ApproveResponse

    @GET("admin/v1/satellites")
    suspend fun satellites(): SatellitesResponse

    @DELETE("admin/v1/satellites/{id}")
    suspend fun deleteSatellite(@Path("id") id: String)

    @POST("admin/v1/satellites/{id}/disabled")
    suspend fun setSatelliteDisabled(@Path("id") id: String, @Body body: SetDisabledRequest)

    @GET("admin/v1/libraries")
    suspend fun libraries(): AdminLibrariesResponse

    @POST("admin/v1/libraries")
    suspend fun createLibrary(@Body body: CreateLibraryRequest): CreateLibraryResponse

    @DELETE("admin/v1/libraries/{id}")
    suspend fun deleteLibrary(@Path("id") id: String)

    @POST("admin/v1/libraries/{id}/collections")
    suspend fun attachCollection(@Path("id") id: String, @Body body: AttachCollectionRequest)

    @DELETE("admin/v1/libraries/{id}/collections/{moduleId}/{collectionId}")
    suspend fun detachCollection(
        @Path("id") id: String,
        @Path("moduleId") moduleId: String,
        @Path("collectionId") collectionId: String,
    )

    @POST("admin/v1/libraries/{id}/refresh")
    suspend fun refreshLibrary(@Path("id") id: String): RefreshLibraryResponse

    @GET("admin/v1/collections")
    suspend fun collections(): CollectionsResponse

    @GET("admin/v1/providers")
    suspend fun providers(): ProvidersResponse

    @POST("admin/v1/providers/chains/{mediaType}")
    suspend fun setChain(@Path("mediaType") mediaType: String, @Body body: SetChainRequest)

    @POST("admin/v1/providers/tmdb")
    suspend fun setTmdb(@Body body: SetTmdbRequest): SavedResponse

    @POST("admin/v1/providers/tvdb")
    suspend fun setTvdb(@Body body: SetTvdbRequest): SavedResponse

    @POST("admin/v1/providers/anidb")
    suspend fun setAnidb(@Body body: SetAnidbRequest): SetAnidbResponse

    @GET("admin/v1/enrich")
    suspend fun enrichStatus(): EnrichStatusResponse

    @POST("admin/v1/enrich")
    suspend fun enrichRun(): EnrichRunResponse

    @GET("admin/v1/sessions")
    suspend fun sessions(): SessionsResponse

    @DELETE("admin/v1/sessions/{id}")
    suspend fun endSession(@Path("id") id: String)
}
