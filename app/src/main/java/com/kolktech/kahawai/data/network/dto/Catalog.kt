package com.kolktech.kahawai.data.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class LibrarySummary(
    val id: String,
    val name: String,
    val mediaType: String,
)

@Serializable
data class LibrariesResponse(val libraries: List<LibrarySummary>)

/// Mirrors `Item` in web/src/api.ts:103-137 — the browse/search/children
/// row shape. `kind` is one of movie|show|episode|album|track.
@Serializable
data class Item(
    val id: String,
    val kind: String,
    val title: String,
    val artist: String? = null,
    val year: Int? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val parentId: String? = null,
    val parentTitle: String? = null,
    val sources: Int = 0,
    val artVersion: Long? = null,
    val premiered: String? = null,
    val resumePositionMs: Long? = null,
    val resumeDurationMs: Long? = null,
    val played: Boolean = false,
    val playCount: Int = 0,
)

@Serializable
data class ItemsResponse(
    val items: List<Item>,
    val total: Int,
    val limit: Int,
    val offset: Int,
)

@Serializable
data class ChildrenResponse(val children: List<Item>)

@Serializable
data class ItemMetadata(
    val overview: String? = null,
    val rating: Double? = null,
    val premiered: String? = null,
    val genres: List<String>? = null,
)

/// Mirrors `VideoStream`/`AudioStream` in crates/kahawai-core/src/media.rs —
/// only the fields the UI shows; the prober reports more than this.
@Serializable
data class VideoStreamInfo(
    val codec: String,
    val width: Int,
    val height: Int,
    val hdr: String? = null,
)

@Serializable
data class AudioStreamInfo(
    val codec: String,
    val channels: Int,
    val language: String? = null,
)

/// Mirrors `MediaInfo` (crates/kahawai-core/src/media.rs) — the raw probe
/// for one source file. `audio`'s index is what `StartSessionRequest.audioTrack`
/// selects by (sessions.rs negotiates against `info.audio[audio_track]` of
/// the FIRST/best source, same ordering as [ItemDetail.sources]).
@Serializable
data class MediaStreams(
    val container: String? = null,
    val durationMs: Long? = null,
    val video: List<VideoStreamInfo> = emptyList(),
    val audio: List<AudioStreamInfo> = emptyList(),
)

/// crates/kahawai-hub/src/api.rs:2199-2233 — one playable source file
/// backing an item.
@Serializable
data class Source(
    val moduleId: String,
    val collectionId: String,
    val pathRel: String,
    val size: Long,
    val available: Boolean,
    val revision: Int,
    val streams: MediaStreams? = null,
)

/// One source file's location, as named inside a `negotiated` verdict.
/// Mirrors the `source` object in `item_query`'s response
/// (crates/kahawai-hub/src/api.rs:2540-2544).
@Serializable
data class NegotiatedSource(
    val moduleId: String,
    val collectionId: String,
    val pathRel: String,
)

/// The converged half of `QUERY /api/v1/items/{id}` — what this client
/// would actually be served, over the source negotiation actually
/// chose. Mirrors the `negotiated` object built in api.rs:2539-2556.
/// Only present on a `QUERY` response; a plain `GET` never fills it.
@Serializable
data class Negotiated(
    val source: NegotiatedSource? = null,
    val mode: String,
    val cost: String,
    val streams: StreamsVerdict? = null,
    /// The unified track list for the negotiated source, each with the
    /// delivery this client would get (tracks.rs `Delivery`) — replaces
    /// the removed `GET /items/{id}/subtitles` call entirely.
    val subtitles: List<SubtitleTrack> = emptyList(),
)

/// Why [ItemDetail.negotiated] is absent instead. The hub used to send a
/// plain string here; it now sends a structured reason.
@Serializable
data class Unavailable(
    val code: String,
    val message: String,
)

/// The detail endpoint (crates/kahawai-hub/src/api.rs:2171-2311)
/// overwrites the list endpoint's numeric `sources` count with the full
/// array of source objects (api.rs:2239) — deliberately not the same
/// shape as [Item.sources]. `sources[].streams` and `negotiated` are
/// only populated by `QUERY`; a plain `GET` reports what was
/// discovered, not what would be served (commit 5147059).
@Serializable
data class ItemDetail(
    val id: String,
    val kind: String,
    val title: String,
    val artist: String? = null,
    val year: Int? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val parentId: String? = null,
    val parentTitle: String? = null,
    val showTitle: String? = null,
    val sources: List<Source> = emptyList(),
    val artVersion: Long? = null,
    val premiered: String? = null,
    val resumePositionMs: Long? = null,
    val resumeDurationMs: Long? = null,
    val played: Boolean = false,
    val playCount: Int = 0,
    val metadata: ItemMetadata? = null,
    val negotiated: Negotiated? = null,
    /// Set instead of `negotiated` when there is nothing to negotiate:
    /// a show/album with no source of its own, or every mediahost for
    /// this item unreachable right now. Not an error — the item still
    /// renders.
    val unavailable: Unavailable? = null,
    /// The file's own chapters, on the item's timeline — present on both
    /// GET and QUERY (crates/kahawai-hub/src/api.rs `ItemDetailResponse`).
    /// Empty when the file declares none and when nothing has looked yet.
    val chapters: List<Chapter> = emptyList(),
    /// HUB-37: the recap/intro/credits boundaries the hub's background
    /// sweep found for this item — only ever populated on a QUERY
    /// response (`ItemQueryResult`, api.rs); a plain GET always reports
    /// this empty. Empty also means "nothing found" and "nothing has
    /// analysed yet" — a client cannot act on the difference.
    val segments: List<Segment> = emptyList(),
)
