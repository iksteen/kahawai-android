package com.kolktech.kahawai.ui.player

import android.graphics.Color
import androidx.media3.ui.CaptionStyleCompat
import org.junit.Assert.assertEquals
import org.junit.Test

/// Locks the intent: an outline, and nothing painted behind the glyphs.
/// media3's own DEFAULT is white-on-black, so a style that silently fell
/// back to it would put the box back without anything else failing.
class TextSubtitleStyleTest {

    @Test
    fun `cues are drawn with a black outline and no background`() {
        assertEquals(Color.WHITE, TEXT_SUBTITLE_STYLE.foregroundColor)
        assertEquals(CaptionStyleCompat.EDGE_TYPE_OUTLINE, TEXT_SUBTITLE_STYLE.edgeType)
        assertEquals(Color.BLACK, TEXT_SUBTITLE_STYLE.edgeColor)
        assertEquals(Color.TRANSPARENT, TEXT_SUBTITLE_STYLE.backgroundColor)
        assertEquals(Color.TRANSPARENT, TEXT_SUBTITLE_STYLE.windowColor)
    }

    @Test
    fun `it is not media3's white-on-black default`() {
        assertEquals(Color.BLACK, CaptionStyleCompat.DEFAULT.backgroundColor)
    }
}
