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

    // computeOriginCorrection

    @Test
    fun `treats null partBaseMs as zero`() {
        val correction = computeOriginCorrection(partBaseMs = null, localMs = 5_000, currentOffsetMs = 0)
        assertEquals(5_000L, correction.correctedOffsetMs)
    }

    @Test
    fun `sums partBaseMs and local position`() {
        val correction = computeOriginCorrection(partBaseMs = 120_000, localMs = 5_000, currentOffsetMs = 0)
        assertEquals(125_000L, correction.correctedOffsetMs)
    }

    @Test
    fun `is in bounds when within the default 60s tolerance`() {
        val correction = computeOriginCorrection(partBaseMs = 0, localMs = 45_000, currentOffsetMs = 44_000)
        assertEquals(true, correction.inBounds)
    }

    @Test
    fun `is out of bounds when the correction is wildly different`() {
        val correction = computeOriginCorrection(partBaseMs = 0, localMs = 200_000, currentOffsetMs = 0)
        assertEquals(false, correction.inBounds)
    }

    @Test
    fun `honors a custom bound`() {
        val correction = computeOriginCorrection(partBaseMs = 0, localMs = 1_500, currentOffsetMs = 0, boundMs = 1_000)
        assertEquals(false, correction.inBounds)
    }

    // matchesSideloadedTrackId

    @Test
    fun `matches a track id rewritten with a MergingMediaPeriod child prefix`() {
        assertEquals(true, matchesSideloadedTrackId(formatId = "1:1234", wantedId = "1234"))
    }

    @Test
    fun `matches a plain track id with no prefix`() {
        assertEquals(true, matchesSideloadedTrackId(formatId = "1234", wantedId = "1234"))
    }

    @Test
    fun `does not match a different track id`() {
        assertEquals(false, matchesSideloadedTrackId(formatId = "1:1234", wantedId = "5678"))
    }

    @Test
    fun `does not match a null format id`() {
        assertEquals(false, matchesSideloadedTrackId(formatId = null, wantedId = "1234"))
    }

    // subtitleVttUrl

    @Test
    fun `builds a vtt url with the shift negated from offset`() {
        val url = subtitleVttUrl(baseUrl = "https://hub.local", itemId = "item1", trackId = 42, offsetMs = 5_000)
        assertEquals("https://hub.local/api/v1/items/item1/subtitles/42.vtt?shift_ms=-5000", url)
    }

    @Test
    fun `trims a trailing slash off the base url`() {
        val url = subtitleVttUrl(baseUrl = "https://hub.local/", itemId = "item1", trackId = 42, offsetMs = 0)
        assertEquals("https://hub.local/api/v1/items/item1/subtitles/42.vtt?shift_ms=0", url)
    }
}
