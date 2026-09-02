@file:OptIn(UnstableApi::class)

package com.kolktech.kahawai.ui.player

import android.graphics.Color
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat

/// How "text" delivery (SRT/VTT through media3's SubtitleView) is drawn:
/// white glyphs with a black outline and no box behind them, which is what
/// every desktop player settles on (VLC, mpv) because it stays legible over
/// both a snowfield and a night scene without covering picture.
///
/// Without this the cues came up white on a solid black rectangle. That is
/// not something this app chose: PlayerView asks its SubtitleView for the
/// platform's captioning style (`setUserDefaultStyle()` in PlayerView's
/// constructor), and on a device where the viewer has never opened the
/// system captioning settings — the out-of-the-box state on Android TV —
/// that resolves to `CaptionStyleCompat.DEFAULT`, whose background colour
/// is `Color.BLACK`. The box is the platform default showing through, not
/// styling that came with the subtitle.
///
/// Deliberately fixed rather than derived from the system style: this is
/// the one caption appearance the app renders in every mode, and a device
/// caption preference that reintroduced a background here would disagree
/// with the ASS and bitmap overlays, which draw their own way and have no
/// notion of it.
///
/// Embedded cue styling is left enabled, so a track that genuinely carries
/// its own colours (a flattened ASS rendition, say) still overrides these.
internal val TEXT_SUBTITLE_STYLE = CaptionStyleCompat(
    Color.WHITE,
    // No box behind the glyphs, and none behind the line's full width
    // either — windowColor is the second, wider one.
    Color.TRANSPARENT,
    Color.TRANSPARENT,
    CaptionStyleCompat.EDGE_TYPE_OUTLINE,
    Color.BLACK,
    // null keeps whatever typeface the view already has.
    null,
)
