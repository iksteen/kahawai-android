package com.kolktech.kahawai.playback

import android.content.Context
import android.hardware.display.DisplayManager
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import android.view.Display
import com.kolktech.kahawai.data.network.dto.CapabilityProfile
import com.kolktech.kahawai.data.network.dto.TargetDuration
import com.kolktech.kahawai.data.network.dto.VideoCap
import kotlin.math.max
import kotlin.math.roundToInt

/// Probes the device for a [CapabilityProfile], the same way
/// web/src/capabilities.ts probes the browser (`MediaSource.isTypeSupported`)
/// rather than hardcoding a claim — HUB-14 negotiation is only as good
/// as what the client actually reports. Built once per process; decoder
/// support and the display don't change while the app is running.
object CapabilityProfileBuilder {
    private val VIDEO_CODECS = listOf(
        "h264" to MediaFormat.MIMETYPE_VIDEO_AVC,
        "hevc" to MediaFormat.MIMETYPE_VIDEO_HEVC,
        "vp9" to MediaFormat.MIMETYPE_VIDEO_VP9,
        "av1" to MediaFormat.MIMETYPE_VIDEO_AV1,
    )
    private val AUDIO_CODECS = listOf(
        "aac" to MediaFormat.MIMETYPE_AUDIO_AAC,
        "mp3" to MediaFormat.MIMETYPE_AUDIO_MPEG,
        "opus" to MediaFormat.MIMETYPE_AUDIO_OPUS,
        "flac" to MediaFormat.MIMETYPE_AUDIO_FLAC,
    )

    fun build(context: Context): CapabilityProfile {
        val decodedMimes = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            .asSequence()
            .filter { !it.isEncoder }
            .flatMap { it.supportedTypes.asSequence() }
            .toSet()

        val video = VIDEO_CODECS.filter { (_, mime) -> mime in decodedMimes }.map { (name, _) -> VideoCap(codec = name) }
        val audio = AUDIO_CODECS.filter { (_, mime) -> mime in decodedMimes }.map { (name, _) -> name }

        val display = context.getSystemService(DisplayManager::class.java)
            ?.getDisplay(Display.DEFAULT_DISPLAY)
        val metrics = context.resources.displayMetrics
        val hdr = display?.let(::supportsHdr) == true

        return CapabilityProfile(
            containers = listOf("mp4", "matroska"),
            video = video,
            audio = audio,
            // 0 = unlimited: like browsers (web/src/capabilities.ts:8-9),
            // Android's audio pipeline downmixes multichannel itself, so
            // capping here would just force an avoidable re-encode.
            maxAudioChannels = 0,
            maxHeight = max(metrics.widthPixels, metrics.heightPixels),
            maxFps = display?.refreshRate?.roundToInt(),
            hdr = hdr,
            maxBandwidthKbps = null,
            // Faithful ASS (libass, ass-kt) and bitmap-overlay (PGS/VobSub
            // display-set) rendering are both wired into the player now —
            // claim the richest tier the hub can offer instead of forcing
            // everything through flattened VTT or a burned-in encode.
            assRender = true,
            graphicsOverlay = true,
            // Media3's TextRenderer reads WebVTT natively — the only
            // format every text rung (converted SRT, flattened ASS,
            // OCR) is delivered as.
            vttRender = true,
            // ExoPlayer times out an idle HLS playlist at 3.5x the
            // declared EXT-X-TARGETDURATION (HUB-17) — the old fixed
            // 2s constant is what caused that hang, so this client
            // needs the measured, keyframe-bound truth rather than
            // `Ignore`.
            targetDuration = TargetDuration.Accurate,
        )
    }

    /// `Display.Mode.getSupportedHdrTypes()` (API 34+) replaced the
    /// deprecated `Display.HdrCapabilities` path, but minSdk is 26 — the
    /// deprecated call is still the only option below API 34.
    private fun supportsHdr(display: Display): Boolean {
        if (Build.VERSION.SDK_INT >= 34) {
            return display.mode.supportedHdrTypes.isNotEmpty()
        }
        @Suppress("DEPRECATION")
        return display.hdrCapabilities?.supportedHdrTypes?.isNotEmpty() == true
    }
}
