package com.kolktech.kahawai.ui.player.subtitle

import io.github.peerless2012.ass.AssFrame
import io.github.peerless2012.ass.AssTex
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/// The distinction the render loop got wrong: "identical to the last frame"
/// and "nothing on screen now" are different answers, and only the first one
/// means keep what is up.
class AssFrameShouldApplyTest {

    private fun frame(changed: Int) = AssFrame(emptyArray<AssTex>(), changed)

    @Test
    fun `a null frame comes through, so the last cue comes down`() {
        assertTrue(assFrameShouldApply(null))
    }

    @Test
    fun `an unchanged frame is skipped, leaving the cue up`() {
        assertFalse(assFrameShouldApply(frame(changed = 0)))
    }

    @Test
    fun `a frame whose positions moved is applied`() {
        assertTrue(assFrameShouldApply(frame(changed = 1)))
    }

    @Test
    fun `a frame whose content changed is applied`() {
        assertTrue(assFrameShouldApply(frame(changed = 2)))
    }
}
