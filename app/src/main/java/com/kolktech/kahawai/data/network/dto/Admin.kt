package com.kolktech.kahawai.data.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/// DTOs for the hub's `/admin/v1/*` surface (crates/kahawai-hub/src/api.rs:109-176),
/// mirroring the reference web client's types in
/// `~/code/kahawai/web/src/api.ts:661-874`. Requires the JWT `admin`
/// claim — see [com.kolktech.kahawai.data.auth.TokenStore.isAdmin].

@Serializable
data class PendingEnrollment(
    val csrFingerprint: String,
    val moduleType: String,
    val moduleId: String,
    val name: String,
)

@Serializable
data class EnrollmentsResponse(val pending: List<PendingEnrollment>)

@Serializable
data class ApproveRequest(val code: String)

@Serializable
data class ApproveResponse(val approved: String)

/// One verified encoder and what it was measured doing (HUB-36). Speeds
/// are realtime multiples; null = never measured, not the same as slow.
@Serializable
data class EncoderCap(
    val codec: String,
    val element: String,
    val hardware: Boolean,
    val speed1080: Double? = null,
    val speed2160: Double? = null,
)

@Serializable
data class SatelliteCaps(
    val encoders: List<EncoderCap> = emptyList(),
    val maxSessions: Int? = null,
    val tonemap: Boolean? = null,
    val tonemapSpeed1080: Double? = null,
    val tonemapSpeed2160: Double? = null,
)

/// What a box has ACHIEVED on a kind of work, as opposed to what its
/// benchmark claims. `class` is `{res}|{src}|{dst}[|tm]`.
@Serializable
data class PaceRow(
    @SerialName("class") val cls: String,
    val multiple: Double,
)

@Serializable
data class Satellite(
    val moduleId: String,
    val moduleType: String,
    val name: String,
    val certFingerprint: String,
    val connected: Boolean,
    val disabled: Boolean,
    val capabilities: SatelliteCaps? = null,
    val pace: List<PaceRow> = emptyList(),
    val linkBytesPerSec: Long? = null,
)

@Serializable
data class SatellitesResponse(val satellites: List<Satellite>)

@Serializable
data class SetDisabledRequest(val disabled: Boolean)

@Serializable
data class AdminLibraryCollection(
    val moduleId: String,
    val collectionId: String,
    val hostName: String? = null,
)

/// "Admin" prefix distinguishes this from the client-facing
/// [LibrarySummary]/[LibrariesResponse] in Catalog.kt — different shape,
/// different (admin-only) endpoint.
@Serializable
data class AdminLibrary(
    val id: String,
    val name: String,
    val mediaType: String,
    val collections: List<AdminLibraryCollection> = emptyList(),
)

@Serializable
data class AdminLibrariesResponse(val libraries: List<AdminLibrary>)

@Serializable
data class ScanState(
    val scanned: Int,
    val failed: Int,
    val skipped: Int,
    val complete: Boolean,
)

@Serializable
data class CollectionInfo(
    val moduleId: String,
    val collectionId: String,
    val hostName: String? = null,
    val mediaType: String,
    val connected: Boolean,
    val scan: ScanState? = null,
)

@Serializable
data class CollectionsResponse(val collections: List<CollectionInfo>)

@Serializable
data class CreateLibraryRequest(val name: String, val mediaType: String)

@Serializable
data class CreateLibraryResponse(val id: String)

@Serializable
data class AttachCollectionRequest(val moduleId: String, val collectionId: String)

@Serializable
data class RefreshLibraryResponse(val asked: Int, val offline: Int)

/// HUB-5 provider precedence: earlier providers own a field, later ones
/// only fill what's left empty.
@Serializable
data class ProviderChain(val order: List<String>, val default: List<String>)

@Serializable
data class ProviderConfigured(val configured: Boolean)

@Serializable
data class ProvidersResponse(
    val tmdb: ProviderConfigured,
    val tvdb: ProviderConfigured,
    val anidb: ProviderConfigured,
    val chains: Map<String, ProviderChain> = emptyMap(),
)

@Serializable
data class SetChainRequest(val order: List<String>)

@Serializable
data class SetTmdbRequest(val apiKey: String)

@Serializable
data class SetTvdbRequest(val apiKey: String, val pin: String? = null)

@Serializable
data class SetAnidbRequest(val username: String, val password: String, val udpApiKey: String? = null)

@Serializable
data class SetAnidbResponse(val saved: Boolean, val verified: Boolean, val error: String? = null)

@Serializable
data class SavedResponse(val saved: Boolean)

@Serializable
data class EnrichStatusResponse(val running: Boolean, val matched: Int, val weak: Int, val missed: Int)

@Serializable
data class EnrichRunResponse(val started: Boolean)

@Serializable
data class AdminSession(
    val sessionId: String,
    val username: String? = null,
    val title: String? = null,
    val mode: String,
    val moduleId: String,
    val idleSecs: Long,
    val streams: StreamsVerdict? = null,
)

@Serializable
data class SessionsResponse(val sessions: List<AdminSession>)
