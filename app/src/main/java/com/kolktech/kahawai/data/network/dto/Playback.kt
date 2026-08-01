package com.kolktech.kahawai.data.network.dto

import kotlinx.serialization.Serializable

/// Mirrors `VideoCap` in crates/kahawai-core/src/media.rs:127-137.
@Serializable
data class VideoCap(
    val codec: String,
    val maxProfile: String? = null,
    val maxLevel: String? = null,
)

/// Mirrors `CapabilityProfile` in crates/kahawai-core/src/media.rs:99-125
/// — sent verbatim on every session start; the hub negotiates
/// direct/remux/transcode per stream from it (HUB-14).
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
