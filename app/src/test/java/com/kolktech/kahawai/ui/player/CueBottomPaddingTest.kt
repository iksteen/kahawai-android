package com.kolktech.kahawai.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class CueBottomPaddingTest {

    @Test
    fun `a letterboxed frame already clears part of the controls`() {
        // 818 tall in a 1080 tall view: 131 of the controls' 200 is over the
        // letterbox, so only the rest has to come out of the frame.
        assertEquals(69, cueBottomPaddingPx(818f, 1080f, 200f))
    }

    @Test
    fun `a frame that clears them entirely needs no padding`() {
        assertEquals(0, cueBottomPaddingPx(600f, 1080f, 200f))
        assertEquals(0, cueBottomPaddingPx(1080f, 1080f, 0f))
    }

    @Test
    fun `an overflowing frame is padded by what hangs off the screen`() {
        // Zoom(crop): 1280 tall in a 1080 view hangs 100 off each end, which
        // is where cues were landing before any controls came into it.
        assertEquals(100, cueBottomPaddingPx(1280f, 1080f, 0f))
        assertEquals(300, cueBottomPaddingPx(1280f, 1080f, 200f))
    }
}
