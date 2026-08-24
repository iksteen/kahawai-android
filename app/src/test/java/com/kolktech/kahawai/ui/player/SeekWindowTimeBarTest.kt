package com.kolktech.kahawai.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class SeekWindowTimeBarTest {

    // seekWindowBandPx

    @Test
    fun `band spans the window between start and buffered`() {
        val band = seekWindowBandPx(startMs = 10_000, endMs = 20_000, durationMs = 100_000, leftPx = 0, rightPx = 1000)
        assertEquals(100..200, band)
    }

    @Test
    fun `band is null when nothing is buffered past the window start`() {
        val band = seekWindowBandPx(startMs = 20_000, endMs = 20_000, durationMs = 100_000, leftPx = 0, rightPx = 1000)
        assertEquals(null, band)
    }

    @Test
    fun `band is null without a known duration`() {
        val band = seekWindowBandPx(startMs = 0, endMs = 20_000, durationMs = 0, leftPx = 0, rightPx = 1000)
        assertEquals(null, band)
    }

    // leanbackSeekIncrementMs

    @Test
    fun `a lone press nudges by one second`() {
        assertEquals(1_000L, leanbackSeekIncrementMs(repeatCount = 0))
    }

    @Test
    fun `increment ramps up the longer the key is held`() {
        assertEquals(1_000L, leanbackSeekIncrementMs(repeatCount = 9))
        assertEquals(5_000L, leanbackSeekIncrementMs(repeatCount = 10))
        assertEquals(5_000L, leanbackSeekIncrementMs(repeatCount = 29))
        assertEquals(15_000L, leanbackSeekIncrementMs(repeatCount = 30))
        assertEquals(15_000L, leanbackSeekIncrementMs(repeatCount = 59))
        assertEquals(30_000L, leanbackSeekIncrementMs(repeatCount = 60))
        assertEquals(30_000L, leanbackSeekIncrementMs(repeatCount = 1000))
    }
}
