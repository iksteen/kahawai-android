package com.kolktech.kahawai.data.network.dto

import kotlinx.serialization.Serializable

/// Mirrors `TrackListing`/`Track` in crates/kahawai-hub/src/tracks.rs:45-80 —
/// one subtitle track plus what it means for THIS client. `delivery` is
/// one of "text"|"ass"|"overlay"|"burn"|"none" (crate::tracks::Delivery),
/// computed from the capability bits the request declared — never stored,
/// so the same track can list differently for different clients.
@Serializable
data class SubtitleTrack(
    val id: Long,
    val itemId: String,
    val origin: String, // "embedded" | "sidecar" | "downloaded" | "ocr"
    val streamIndex: Long? = null,
    val format: String, // srt/vtt/ass/ssa/pgs/vobsub/dvdsub/...
    val language: String? = null,
    val label: String? = null,
    val machine: Boolean = false,
    val derivedFrom: Long? = null,
    val delivery: String, // "text" | "ass" | "overlay" | "burn" | "none"
    val note: String = "",
)

@Serializable
data class SubtitlesResponse(val subtitles: List<SubtitleTrack>)

/// `GET /api/v1/items/{id}/fonts` — embedded font names for ASS tracks;
/// bytes are fetched per-index via the font endpoint (api.rs:1760-1781).
@Serializable
data class FontsResponse(val fonts: List<String>)
