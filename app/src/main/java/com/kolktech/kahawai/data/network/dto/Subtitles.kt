package com.kolktech.kahawai.data.network.dto

import java.util.Locale
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

/// "English", not "en" — the hub only sends the raw ISO code, so the
/// full name is resolved on-device. `label` (a downloaded track's
/// provider release name, e.g. "SDH"/"Forced") and `note` (a technical
/// delivery caveat, e.g. "burned in — restarts with a video encode")
/// tack on when present. Shared by every subtitle picker (Detail
/// screen, the player's CC menu) so they read the same everywhere.
fun SubtitleTrack.displayLabel(): String {
    val languageName = language
        ?.let { code -> runCatching { Locale.forLanguageTag(code).getDisplayLanguage(Locale.ENGLISH) }.getOrNull() }
        ?.takeIf { it.isNotBlank() && !it.equals(language, ignoreCase = true) }
    val base = listOfNotNull(languageName ?: language, label?.takeIf { it.isNotBlank() })
        .joinToString(" ")
        .ifBlank { format.uppercase() }
    return if (note.isNotBlank()) "$base ($note)" else base
}

/// `GET /api/v1/items/{id}/fonts` — embedded font names for ASS tracks;
/// bytes are fetched per-index via the font endpoint (api.rs:1760-1781).
@Serializable
data class FontsResponse(val fonts: List<String>)
