package com.kolktech.kahawai.data.network.dto

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/// Mirrors `VideoCap` in crates/kahawai-core/src/media.rs:127-137.
@Serializable
data class VideoCap(
    val codec: String,
    val maxProfile: String? = null,
    val maxLevel: String? = null,
)

/// Mirrors `TargetDuration` in crates/kahawai-core/src/media.rs:129-144
/// (`#[serde(tag = "mode")]`). Required on [CapabilityProfile] — there is
/// no default that is right for every client. ExoPlayer times out an
/// idle playlist at 3.5x the declared value (kahawai-status-checklist.md,
/// HUB-17), so `Accurate` — the measured keyframe-bound truth — is what
/// this client needs; the old constant-and-violate `Ignore` behaviour is
/// what caused that hang in the first place.
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("mode")
sealed class TargetDuration {
    @Serializable
    @SerialName("ignore")
    object Ignore : TargetDuration()

    @Serializable
    @SerialName("accurate")
    object Accurate : TargetDuration()

    @Serializable
    @SerialName("short")
    data class Short(val maxSecs: Int) : TargetDuration()
}

/// Mirrors `CapabilityProfile` in crates/kahawai-core/src/media.rs:99-137
/// — sent verbatim on every session start (HUB-14) and on every item
/// QUERY. The hub negotiates direct/remux/transcode per stream from it.
@Serializable
data class CapabilityProfile(
    val containers: List<String>,
    val video: List<VideoCap>,
    val audio: List<String>,
    val maxAudioChannels: Int,
    val maxHeight: Int? = null,
    val maxFps: Int? = null,
    val hdr: Boolean,
    val maxBandwidthKbps: Int? = null,
    val assRender: Boolean,
    val graphicsOverlay: Boolean,
    // HUB-32e (commit e1b7432): the one text rendering path — a
    // converted SRT, a flattened ASS and an OCR-derived track all
    // deliver as WebVTT, which Media3's built-in TextRenderer always
    // reads, so this stays true. Defaults true on the hub side too:
    // unlike its neighbours, a client saying nothing is assumed to
    // read text, since that's what the conservative fallback has
    // always delivered.
    val vttRender: Boolean = true,
    val targetDuration: TargetDuration,
)

@Serializable
data class StartSessionRequest(
    val itemId: String,
    val mode: String? = null,
    val profile: CapabilityProfile? = null,
    val startMs: Long = 0,
    val audioTrack: Int = 0,
    val videoTrack: Int = 0,
    val subtitleTrack: Long? = null,
)

/// `QUERY /api/v1/items/{id}` (RFC 10008) request body — same inputs a
/// session start takes, minus everything that only matters once
/// actually playing. Mirrors `ItemQuery` in api.rs:2408-2425.
@Serializable
data class ItemQueryRequest(
    val profile: CapabilityProfile? = null,
    val audioTrack: Int = 0,
    val videoTrack: Int = 0,
    val subtitleTrack: Long? = null,
    val mode: String? = null,
)

@Serializable
data class StreamsVerdict(
    val video: String? = null,
    val audio: String? = null,
)

/// Mirrors the `start_session`/`seek_session` response shape
/// (crates/kahawai-hub/src/api.rs:1387-1406). `stream_url` is a
/// byte-range GET for `mode == "direct"`, or an HLS master playlist
/// (`.m3u8`) for `remux`/`transcode`.
@Serializable
data class StartSessionResponse(
    val sessionId: String,
    val mode: String,
    val size: Long? = null,
    val durationMs: Long? = null,
    val partBaseMs: Long? = null,
    val parts: Int = 1,
    val contentType: String,
    val streamUrl: String,
    val streams: StreamsVerdict? = null,
    /// The unified track list computed against THIS session's effective
    /// profile and negotiated source (kahawai commit 1eb6363). The item
    /// QUERY's listing reflects the profile at page load; after a
    /// capability-masked restart the two can disagree, so this is what
    /// PlayerViewModel refreshes [PlayerViewModel.subtitleTracks] from
    /// once a session actually starts.
    val subtitleListing: List<SubtitleTrack> = emptyList(),
)

@Serializable
data class SeekRequest(
    val positionMs: Long,
    val audioTrack: Int? = null,
    val videoTrack: Int? = null,
    val subtitleTrack: Long? = null,
)

/// Seek-restart response (api.rs:1422-1461): same session id/URLs, a
/// new `part_base_ms` — the client re-attaches to the same playlist
/// URL, now serving from the new offset.
@Serializable
data class SeekResponse(
    val partBaseMs: Long? = null,
    val streams: StreamsVerdict? = null,
)

@Serializable
data class ProgressRequest(
    val positionMs: Long,
)

/// One chapter of a file, on the ITEM's timeline (kahawai commit
/// db7d743, "Chapters ride the item from the scan to the seek bar").
/// `endMs` is only what the container states — Matroska usually leaves
/// it out and means "until the next one starts".
@Serializable
data class Chapter(
    val startMs: Long,
    val endMs: Long? = null,
    val title: String? = null,
)

/// One recap/intro/credits boundary the hub's background sweep found
/// for this episode (HUB-37, kahawai commit 4e9956a). `kind` is one of
/// "recap"|"intro"|"credits"; `source` names which analyzer answered
/// ("chapter"|"chromaprint"|"blackframe") and is display-only here.
@Serializable
data class Segment(
    val kind: String,
    val startMs: Long,
    val endMs: Long,
    val source: String,
)
