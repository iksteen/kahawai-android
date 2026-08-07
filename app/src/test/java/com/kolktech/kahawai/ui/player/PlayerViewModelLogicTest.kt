package com.kolktech.kahawai.ui.player

import com.kolktech.kahawai.data.network.dto.Item
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerViewModelLogicTest {

    private fun episode(id: String) = Item(id = id, kind = "episode", title = id)

    // resumePlan

    @Test
    fun `direct mode seeks natively with offset zero`() {
        val plan = resumePlan("direct", startMs = 45_000)
        assertEquals(ResumePlan(offsetMs = 0, startPositionMs = 45_000), plan)
    }

    @Test
    fun `remux mode attaches at zero with offset equal to startMs`() {
        val plan = resumePlan("remux", startMs = 45_000)
        assertEquals(ResumePlan(offsetMs = 45_000, startPositionMs = 0), plan)
    }

    @Test
    fun `transcode mode is treated like remux`() {
        val plan = resumePlan("transcode", startMs = 45_000)
        assertEquals(ResumePlan(offsetMs = 45_000, startPositionMs = 0), plan)
    }

    @Test
    fun `both modes converge when startMs is zero`() {
        assertEquals(ResumePlan(offsetMs = 0, startPositionMs = 0), resumePlan("direct", startMs = 0))
        assertEquals(ResumePlan(offsetMs = 0, startPositionMs = 0), resumePlan("remux", startMs = 0))
    }

    // resolveNextEpisode

    @Test
    fun `returns next sibling id when item is mid-list`() {
        val siblings = listOf(episode("e1"), episode("e2"), episode("e3"))
        val next = resolveNextEpisode(kind = "episode", parentId = "show1", itemId = "e2", siblings = siblings)
        assertEquals("e3", next)
    }

    @Test
    fun `returns null for the last episode`() {
        val siblings = listOf(episode("e1"), episode("e2"), episode("e3"))
        val next = resolveNextEpisode(kind = "episode", parentId = "show1", itemId = "e3", siblings = siblings)
        assertNull(next)
    }

    @Test
    fun `returns null for a non-episode kind`() {
        val siblings = listOf(episode("e1"), episode("e2"))
        val next = resolveNextEpisode(kind = "movie", parentId = "show1", itemId = "e1", siblings = siblings)
        assertNull(next)
    }

    @Test
    fun `returns null when parentId is null`() {
        val siblings = listOf(episode("e1"), episode("e2"))
        val next = resolveNextEpisode(kind = "episode", parentId = null, itemId = "e1", siblings = siblings)
        assertNull(next)
    }

    @Test
    fun `returns null when itemId is not found in siblings`() {
        val siblings = listOf(episode("e1"), episode("e2"))
        val next = resolveNextEpisode(kind = "episode", parentId = "show1", itemId = "missing", siblings = siblings)
        assertNull(next)
    }

    @Test
    fun `returns null for empty siblings`() {
        val next = resolveNextEpisode(kind = "episode", parentId = "show1", itemId = "e1", siblings = emptyList())
        assertNull(next)
    }
}
