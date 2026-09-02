package com.kolktech.kahawai.ui.player

import androidx.media3.common.C
import androidx.media3.common.ColorInfo
import androidx.media3.common.Format
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/// What decides whether cues get dimmed: the transfer function, which is
/// the field that says the panel is being driven in HDR.
class HdrTransferTest {

    private fun video(transfer: Int?, primaries: Int = C.COLOR_SPACE_BT709): Format =
        Format.Builder()
            .setSampleMimeType("video/hevc")
            .setColorInfo(
                ColorInfo.Builder()
                    .setColorSpace(primaries)
                    .apply { transfer?.let { setColorTransfer(it) } }
                    .build(),
            )
            .build()

    @Test
    fun `pq is hdr`() {
        assertTrue(isHdrTransfer(video(C.COLOR_TRANSFER_ST2084, C.COLOR_SPACE_BT2020)))
    }

    @Test
    fun `hlg is hdr`() {
        assertTrue(isHdrTransfer(video(C.COLOR_TRANSFER_HLG, C.COLOR_SPACE_BT2020)))
    }

    @Test
    fun `plain sdr is not`() {
        assertFalse(isHdrTransfer(video(C.COLOR_TRANSFER_SDR)))
    }

    /// Tone-mapped output keeps BT.2020 primaries while losing the HDR
    /// transfer — dimming it would leave SDR cues needlessly grey.
    @Test
    fun `wide-gamut sdr is not hdr`() {
        assertFalse(isHdrTransfer(video(C.COLOR_TRANSFER_SDR, C.COLOR_SPACE_BT2020)))
    }

    @Test
    fun `a format with no colour info at all is not hdr`() {
        assertFalse(isHdrTransfer(Format.Builder().setSampleMimeType("video/avc").build()))
    }

    @Test
    fun `no video track selected is not hdr`() {
        assertFalse(isHdrTransfer(null))
    }
}
