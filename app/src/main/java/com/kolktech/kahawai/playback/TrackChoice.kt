package com.kolktech.kahawai.playback

import com.kolktech.kahawai.data.network.dto.AudioStreamInfo
import com.kolktech.kahawai.data.network.dto.Pref
import com.kolktech.kahawai.data.network.dto.SubtitleTrack

/// HUB-33: which audio and which subtitles a session opens with, and what a
/// pick remembers.
/// A faithful port of the web client's own resolution
/// (web/src/domain/tracks.ts resolveTracks, web/src/domain/subtitles.ts
/// pickSubtitle, web/src/components/Picture.vue chooseSubtitle) over the same
/// preference rows, so a title picked up on one client continues on the other.
///
/// Three layers, most specific first, because a preference is not one thing:
/// THIS item's exact track (the only spelling that can name a downloaded or
/// OCR row, or one of two English tracks), then the series' remembered
/// language (portable across episodes whose mux order differs), then the
/// account's ordered wishlist for this kind of media.

/// Scoped to a series (or a film's own id): the language it's watched in,
/// `off` for none.
internal const val PREF_SUBS = "subs"

/// Scoped to a series: the language it's heard in, or `#N` for a track that
/// declares none.
internal const val PREF_AUDIO = "audio"

/// Scoped to one item: `#N`, the exact stream index. Films only — two
/// English tracks (feature and commentary) is common and no language can
/// tell them apart, while an episode that pinned an index would freeze on
/// it while the rest of the series moved on.
internal const val PREF_AUDIO_TRACK = "audio.track"

/// Scoped to one item: the exact track id, `` for none.
internal const val PREF_SUBS_TRACK = "subs.track"

/// Whether the account's per-media-type list can still affect the answer.
/// It's the only layer that needs to know which library an item is in, and
/// learning that costs a lookup — so when no such list is set, don't.
internal fun needsMediaType(prefs: List<Pref>): Boolean =
    prefs.any { it.scope == "" && (it.key.startsWith("$PREF_SUBS.") || it.key.startsWith("$PREF_AUDIO.")) }

/// Two letters is a match: `en` and `eng` and `en-GB` are the same wish.
private fun langEq(have: String?, want: String): Boolean {
    if (have.isNullOrEmpty()) return false
    val a = have.lowercase()
    val b = want.lowercase()
    return a == b || a.take(2) == b.take(2)
}

/// `#N` — the spelling for a track no language can name.
private fun exactIndex(value: String?, count: Int): Int? =
    value?.removePrefix("#")?.toIntOrNull()?.takeIf { value.startsWith("#") && it in 0 until count }

/// Which audio stream this item opens with, by index into [audio] — the same
/// index `StartSessionRequest.audioTrack` selects by. Track 0 when nothing
/// says otherwise, which is what the hub would have picked anyway.
internal fun resolveAudioTrack(
    prefs: List<Pref>,
    seriesId: String,
    itemId: String,
    mediaType: String,
    originalLanguage: String?,
    audio: List<AudioStreamInfo>,
): Int {
    // Most specific first: THIS item's exact track, since two English tracks
    // - feature and commentary - are common and language cannot express the
    // choice.
    exactIndex(prefValue(prefs, itemId, PREF_AUDIO_TRACK), audio.size)?.let { return it }
    val remembered = prefValue(prefs, seriesId, PREF_AUDIO)
    if (remembered != null) {
        exactIndex(remembered, audio.size)?.let { return it }
        audio.indexOfFirst { langEq(it.language, remembered) }.takeIf { it >= 0 }?.let { return it }
    }
    // `original` is the standing backstop: the implicit final entry of every
    // audio wishlist, and the whole list when none is set.
    val wish = prefValue(prefs, "", "$PREF_AUDIO.$mediaType")
        .orEmpty()
        .split(',')
        .map { it.trim().lowercase() }
        .filter { it.isNotEmpty() }
        .toMutableList()
    if (!wish.contains("original")) wish.add("original")
    for (want in wish) {
        val language = if (want == "original") originalLanguage else want
        if (language.isNullOrEmpty()) continue
        audio.indexOfFirst { langEq(it.language, language) }.takeIf { it >= 0 }?.let { return it }
    }
    return 0
}

/// What an audio pick writes to the series' memory: the language, which is
/// what carries across episodes whose track order differs, or `#N` for a
/// track that declares none.
internal fun rememberedAudioValue(track: AudioStreamInfo?, index: Int): String =
    track?.language?.lowercase()?.takeIf { it.isNotEmpty() } ?: "#$index"

/// A bitmap track: its cues are pictures, not text.
private val IMAGE_FORMATS = setOf("pgs", "vobsub", "dvdsub")

/// Deliveries this client renders itself. A wishlist never auto-picks a
/// burn: that's a server-side video encode, which is not what "I watch this
/// in English" asks for. Burns stay explicit picks.
private val CLIENT_RENDERED = setOf("text", "ass", "overlay")

private fun prefValue(prefs: List<Pref>, scope: String, key: String): String? =
    prefs.firstOrNull { it.scope == scope && it.key == key }?.value

/// The track this item opens with, or null for none — [seriesId] is the
/// item's parent id, or its own id when it has no parent.
internal fun resolveSubtitleTrack(
    prefs: List<Pref>,
    seriesId: String,
    itemId: String,
    mediaType: String,
    tracks: List<SubtitleTrack>,
): SubtitleTrack? {
    // Top precedence: this item's exact remembered row, honoured only while
    // it's still a track this client can be served.
    val exactId = prefValue(prefs, itemId, PREF_SUBS_TRACK)?.toLongOrNull()
    tracks.firstOrNull { it.id == exactId && it.delivery != "none" }?.let { return it }
    return pickSubtitleTrack(subtitleWishlist(prefs, seriesId, mediaType), tracks)
}

/// The languages to try, in order. The series' memory answers alone when it
/// has one — a standing choice for a title beats the account's list, which
/// is what makes "this show in Japanese" stick without touching Settings.
internal fun subtitleWishlist(prefs: List<Pref>, seriesId: String, mediaType: String): List<String> {
    val remembered = prefValue(prefs, seriesId, PREF_SUBS)
    return when {
        remembered == "off" -> emptyList()
        !remembered.isNullOrBlank() -> listOf(remembered.lowercase())
        else -> prefValue(prefs, "", "subs.$mediaType")
            .orEmpty()
            .split(',')
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
    }
}

/// The first track a wishlist would choose, or null. Within one language the
/// BEST reading wins rather than whichever row the listing happened to put
/// first: this client's own ASS renderer, then a server-rasterised overlay,
/// then flattened text — otherwise a client with ASS masked off takes the
/// flattened VTT and never notices the rasterised track behind it.
internal fun pickSubtitleTrack(wishlist: List<String>, tracks: List<SubtitleTrack>): SubtitleTrack? {
    val eligible = tracks.filter { it.delivery in CLIENT_RENDERED && it.format.lowercase() !in IMAGE_FORMATS }
    val rank = { track: SubtitleTrack ->
        when (track.delivery) {
            "ass" -> 0
            "overlay" -> 1
            else -> 2
        }
    }
    for (want in wishlist) {
        val candidates = if (want == "any") {
            eligible
        } else {
            eligible.filter { (it.language ?: "").lowercase().take(2) == want.take(2) }
        }
        candidates.minByOrNull(rank)?.let { return it }
    }
    return null
}

/// What a pick writes to the series' memory: a language, `any` for a track
/// that declares none, `off` for turning subtitles off — which is as much a
/// choice as any track, and has to survive into the next episode.
internal fun rememberedSubsValue(track: SubtitleTrack?): String =
    track?.let { (it.language ?: "any").lowercase() } ?: "off"

/// What a pick writes to this item's memory. Empty deletes the row, which is
/// what "no exact track here" means.
internal fun rememberedSubsTrackValue(track: SubtitleTrack?): String = track?.id?.toString() ?: ""
