package com.kolktech.kahawai.ui.player.subtitle

import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.media3.common.Player
import com.kolktech.kahawai.data.network.ApiClient
import com.kolktech.kahawai.data.network.dto.SubtitleTrack
import com.kolktech.kahawai.data.repository.PlaybackRepository
import com.kolktech.kahawai.ui.player.SubtitleSession
import io.github.peerless2012.ass.Ass
import io.github.peerless2012.ass.AssFrame
import io.github.peerless2012.ass.AssTexType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.util.concurrent.TimeUnit

private const val TAG = "AssSubtitleOverlay"

/// Faithful ASS/SSA rendering (libass via io.github.peerless2012:ass-kt),
/// mirroring the web client's JASSUB integration
/// (web/src/views/Player.tsx:470-562). Driven directly against the
/// ass-kt primitives (Ass/AssTrack/AssRender) rather than through
/// ass-media's AssHandler/AssRenderersFactory: that module auto-detects
/// ASS tracks off ExoPlayer's own Tracks/Format events, which only fire
/// for tracks muxed into the played container. The hub's ASS tracks are
/// the opposite — an out-of-band streamed `.ass` tap keyed to a track id,
/// never muxed into the HLS stream — so there's no such event to hook;
/// header/dialogue are read straight off our own HTTP stream instead,
/// same as JASSUB is fed on the web.
@Composable
fun AssSubtitleOverlay(
    player: Player,
    itemId: String,
    repo: PlaybackRepository,
    track: SubtitleTrack,
    subtitleSession: SubtitleSession,
    resizeMode: Int,
    modifier: Modifier = Modifier,
) {
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var frame by remember(subtitleSession.epoch, track.id) { mutableStateOf<AssFrame?>(null) }
    var frameSize by remember(subtitleSession.epoch, track.id) { mutableStateOf(IntSize.Zero) }

    val ass = remember(subtitleSession.epoch, track.id) { Ass() }
    DisposableEffect(ass) { onDispose { ass.release() } }

    val assRender = remember(ass) { ass.createRender() }
    DisposableEffect(assRender) { onDispose { assRender.release() } }

    // Fonts, then the track (header + growing Dialogue feed from the
    // session's live .ass tap).
    LaunchedEffect(subtitleSession.epoch, track.id) {
        try {
            val fonts = repo.fonts(itemId).fonts
            fonts.forEachIndexed { index, name ->
                val bytes = withContext(Dispatchers.IO) {
                    fetchBytes("${ApiClient.baseUrl().trimEnd('/')}/api/v1/items/$itemId/fonts/$index")
                }
                if (bytes != null) ass.addFont(name, bytes) else Log.w(TAG, "font $name ($index) failed to load")
            }
        } catch (e: Exception) {
            Log.w(TAG, "font list failed for item=$itemId", e)
        }

        val assTrack = ass.createTrack()
        assRender.setTrack(assTrack)

        val url = "${subtitleSession.streamBaseUrl}subs-${track.id}.ass"
        withContext(Dispatchers.IO) {
            try {
                val client = ApiClient.authenticatedOkHttpClient().newBuilder()
                    // Long-lived, growing stream (header, then Dialogue
                    // lines as the demux pass reaches them) — must not
                    // time out while idle.
                    .readTimeout(0, TimeUnit.SECONDS)
                    .build()
                client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    val source = response.body?.source() ?: return@use
                    val header = StringBuilder()
                    var sawEvents = false
                    var headerDone = false
                    // Accumulate from the very start ([Script Info],
                    // [V4+ Styles], ...) through the Events "Format:"
                    // line — libass needs all of it, same span JASSUB's
                    // subContent covers on the web.
                    while (isActive && !headerDone) {
                        val line = source.readUtf8Line() ?: break
                        header.append(line).append('\n')
                        if (!sawEvents && line.trim().equals("[Events]", ignoreCase = true)) sawEvents = true
                        if (sawEvents && line.trim().startsWith("Format:", ignoreCase = true)) headerDone = true
                    }
                    if (!headerDone) return@use
                    assTrack.readBuffer(header.toString().toByteArray())
                    // Each subsequent Dialogue line is re-fed with the
                    // section marker so libass's line-oriented parser
                    // stays in [Events] context — mirrors JASSUB's
                    // `processData('[Events]\n' + lines)`, just per-line
                    // instead of batched (dialogue lines never embed a
                    // newline, so this is equally correct).
                    while (isActive) {
                        val line = source.readUtf8Line() ?: break
                        if (line.isBlank()) continue
                        assTrack.readBuffer("[Events]\n$line\n".toByteArray())
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, ".ass tap failed for track=${track.id}", e)
            }
        }
    }

    // Recompute libass's storage/frame size whenever the displayed
    // content box changes (container resize, rotation, resize-mode
    // toggle, or the video's own size becoming known shortly after
    // prepare), and drive redraws off the player clock. renderFrame is a
    // synchronized native call over potentially-uncached glyphs — run it
    // off the main thread, mirroring the library's own AssExecutor.
    LaunchedEffect(subtitleSession.epoch, track.id, containerSize, resizeMode) {
        while (isActive) {
            val videoSize = player.videoSize
            if (videoSize.width > 0 && videoSize.height > 0 && containerSize.width > 0 && containerSize.height > 0) {
                val rect = contentRectFor(
                    containerW = containerSize.width.toFloat(),
                    containerH = containerSize.height.toFloat(),
                    videoW = videoSize.width.toFloat(),
                    videoH = videoSize.height.toFloat(),
                    resizeMode = resizeMode,
                )
                val w = rect.width.toInt().coerceAtLeast(2)
                val h = rect.height.toInt().coerceAtLeast(2)
                if (frameSize.width != w || frameSize.height != h) {
                    assRender.setStorageSize(videoSize.width, videoSize.height)
                    assRender.setFrameSize(w, h)
                    frameSize = IntSize(w, h)
                }
                val t = player.currentPosition + subtitleSession.offsetMs
                val result = withContext(Dispatchers.Default) { assRender.renderFrame(t, AssTexType.BITMAP_ALPHA) }
                if (result != null && result.changed != 0) frame = result
            }
            delay(200)
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it },
    ) {
        val f = frame ?: return@Canvas
        if (frameSize.width <= 0 || frameSize.height <= 0) return@Canvas
        val videoSize = player.videoSize
        val rect = contentRectFor(size.width, size.height, videoSize.width.toFloat(), videoSize.height.toFloat(), resizeMode)
        val scaleX = rect.width / frameSize.width
        val scaleY = rect.height / frameSize.height
        drawIntoCanvas { canvas ->
            val paint = Paint()
            f.images?.forEach { tex ->
                val bitmap = tex.bitmap ?: return@forEach
                // ASS colors pack RGB in the upper 3 bytes and alpha
                // INVERTED (0 = opaque) in the low byte; tex.bitmap is an
                // alpha-only mask, tinted via Paint.color — same formula
                // as AssSubtitleCanvasView.onDraw (lib_ass_media).
                val r = (tex.color shr 24) and 0xFF
                val g = (tex.color shr 16) and 0xFF
                val b = (tex.color shr 8) and 0xFF
                val a = 0xFF - (tex.color and 0xFF)
                paint.color = (a shl 24) or (r shl 16) or (g shl 8) or b
                val dst = RectF(
                    rect.left + tex.x * scaleX,
                    rect.top + tex.y * scaleY,
                    rect.left + (tex.x + bitmap.width) * scaleX,
                    rect.top + (tex.y + bitmap.height) * scaleY,
                )
                canvas.nativeCanvas.drawBitmap(bitmap, null, dst, paint)
            }
        }
    }
}

private fun fetchBytes(url: String): ByteArray? {
    ApiClient.authenticatedOkHttpClient().newCall(Request.Builder().url(url).build()).execute().use { response ->
        if (!response.isSuccessful) return null
        return response.body?.bytes()
    }
}
