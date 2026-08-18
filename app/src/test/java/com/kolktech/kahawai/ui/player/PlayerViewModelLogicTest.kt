package com.kolktech.kahawai.ui.player

import com.kolktech.kahawai.R
import com.kolktech.kahawai.data.network.dto.Chapter
import com.kolktech.kahawai.data.network.dto.Item
import com.kolktech.kahawai.data.network.dto.Segment
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

    // skippableSegment / skipLabelRes / skipTargetMs

    private fun segment(kind: String, startMs: Long, endMs: Long, source: String = "chromaprint") =
        Segment(kind = kind, startMs = startMs, endMs = endMs, source = source)

    @Test
    fun `finds the segment the playhead is inside`() {
        val intro = segment("intro", 10_000, 30_000)
        val found = skippableSegment(listOf(intro), posMs = 15_000)
        assertEquals(intro, found)
    }

    @Test
    fun `returns null before the segment starts`() {
        val intro = segment("intro", 10_000, 30_000)
        assertNull(skippableSegment(listOf(intro), posMs = 9_999))
    }

    @Test
    fun `returns null inside the tail where the button would be unpressable`() {
        val intro = segment("intro", 10_000, 30_000)
        // 30_000 - SKIP_TAIL_MS (1_500) = 28_500; at or past that, hidden.
        assertNull(skippableSegment(listOf(intro), posMs = 28_500))
    }

    @Test
    fun `honors a custom tail`() {
        val intro = segment("intro", 10_000, 30_000)
        assertNull(skippableSegment(listOf(intro), posMs = 25_000, tailMs = 10_000))
    }

    @Test
    fun `ignores a segment of an unknown kind`() {
        val weird = segment("preview", 10_000, 30_000)
        assertNull(skippableSegment(listOf(weird), posMs = 15_000))
    }

    @Test
    fun `first match wins when segments overlap`() {
        val recap = segment("recap", 0, 20_000)
        val intro = segment("intro", 10_000, 30_000)
        val found = skippableSegment(listOf(recap, intro), posMs = 15_000)
        assertEquals(recap, found)
    }

    @Test
    fun `maps each known kind to its own label resource`() {
        assertEquals(R.string.player_skip_recap, skipLabelRes(segment("recap", 0, 1_000)))
        assertEquals(R.string.player_skip_intro, skipLabelRes(segment("intro", 0, 1_000)))
        assertEquals(R.string.player_skip_credits, skipLabelRes(segment("credits", 0, 1_000)))
        assertNull(skipLabelRes(segment("preview", 0, 1_000)))
        assertNull(skipLabelRes(null))
    }

    @Test
    fun `skip target lands at the segment end when short of the duration`() {
        val credits = segment("credits", 100_000, 118_000)
        assertEquals(118_000L, skipTargetMs(credits, durationMs = 130_000))
    }

    @Test
    fun `skip target never lands on the very last second of the file`() {
        val credits = segment("credits", 100_000, 120_000)
        assertEquals(119_000L, skipTargetMs(credits, durationMs = 120_000))
    }

    @Test
    fun `skip target is untouched when duration is unknown`() {
        val credits = segment("credits", 100_000, 120_000)
        assertEquals(120_000L, skipTargetMs(credits, durationMs = 0))
    }

    // chapterMarkTimesMs

    @Test
    fun `drops a chapter at zero and keeps the rest`() {
        val chapters = listOf(
            Chapter(startMs = 0, title = "Start"),
            Chapter(startMs = 60_000, title = "Two"),
        )
        val marks = chapterMarkTimesMs(chapters, durationMs = 120_000)
        assertEquals(listOf(60_000L), marks.toList())
    }

    @Test
    fun `drops a chapter at or past the duration`() {
        val chapters = listOf(Chapter(startMs = 60_000), Chapter(startMs = 120_000))
        val marks = chapterMarkTimesMs(chapters, durationMs = 120_000)
        assertEquals(listOf(60_000L), marks.toList())
    }

    @Test
    fun `no marks when duration is unknown`() {
        val chapters = listOf(Chapter(startMs = 60_000))
        assertEquals(0, chapterMarkTimesMs(chapters, durationMs = 0).size)
    }
}
