@file:OptIn(UnstableApi::class)

package com.kolktech.kahawai.ui.player

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.DefaultTimeBar
import com.kolktech.kahawai.R

/// Where the band lands on the bar, in pixels, or null if there's nothing
/// to draw. [leftPx]/[rightPx] are the bar's own drawing edges.
internal fun seekWindowBandPx(
    startMs: Long,
    endMs: Long,
    durationMs: Long,
    leftPx: Int,
    rightPx: Int,
): IntRange? {
    if (durationMs <= 0 || rightPx <= leftPx) return null
    val widthPx = rightPx - leftPx
    fun px(ms: Long) = leftPx + (widthPx * ms.coerceIn(0, durationMs) / durationMs).toInt()
    val start = px(startMs)
    val end = px(endMs)
    return if (end > start) start..end else null
}

/// A [DefaultTimeBar] that shades the part of the video reachable right
/// now — for an HLS session the window the hub has produced, for a direct
/// one the whole byte-range-seekable file — instead of the stock played
/// fill, which shades everything left of the playhead whether or not
/// seeking there costs a pipeline restart. The scrubber already says
/// where playback is; what the bar has to say is where it can go.
///
/// The band's end arrives the normal way, as the player's buffered
/// position (see PlayerViewModel's ForwardingPlayer); only its start needs
/// [windowStartMs], since a time bar has no concept of a band that doesn't
/// begin at zero. media3's own fills are all turned off in
/// kw_player_control_view.xml (transparent) rather than here — including
/// the unplayed one, which it only ever draws from the playhead rightward —
/// so the two below are the only ones drawn.
class SeekWindowTimeBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : DefaultTimeBar(context, attrs) {

    /// Read at draw time rather than pushed on change: the value moves
    /// with every hub seek, and the bar redraws on its own schedule.
    var windowStartMs: () -> Long = { 0 }

    private val bandPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.kahawai_timebar_seekable)
    }

    /// The bar's own unpainted length. Drawn here rather than left to
    /// DefaultTimeBar, which only draws its unplayed colour from the
    /// playhead RIGHTWARD — everything left of it is the played and
    /// buffered fills, which this bar turns off. Relying on it left the bar
    /// with nothing at all drawn before the band, so a session resumed
    /// mid-episode looked like a bar that started partway across the
    /// screen.
    private val basePaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.kahawai_timebar_base)
    }

    /// Mirrors DefaultTimeBar's own layout arithmetic, which keeps its
    /// rects private: a bar_height-tall strip centred in the view
    /// (BAR_GRAVITY_CENTER, the default), inset by half the largest
    /// scrubber so the scrubber can't be drawn off the edge. Derived from
    /// media3's public defaults, so it follows them across version bumps —
    /// it only goes wrong if the layout XML overrides one of these sizes
    /// without this being updated to match.
    private val barHeightPx = dpToPx(DEFAULT_BAR_HEIGHT_DP)
    private val scrubberPaddingPx = (
        dpToPx(
            maxOf(
                DEFAULT_SCRUBBER_DISABLED_SIZE_DP,
                DEFAULT_SCRUBBER_ENABLED_SIZE_DP,
                DEFAULT_SCRUBBER_DRAGGED_SIZE_DP,
            ),
        ) + 1
        ) / 2

    /// When the controls hide, the bar itself lingers on screen for a
    /// moment in media3's "minimal mode" — which shrinks the scrubber
    /// away. That was fine while the played fill still marked the
    /// playhead; with the fill gone it would leave a bar saying nothing
    /// about where playback is. The scrubber is this bar's only position
    /// marker, so both hides are declined. The boolean variant also drops
    /// the scrubber's padding, which the bar's own edges — and so the band
    /// measured against them — would otherwise shift by.
    override fun hideScrubber(disableScrubberPadding: Boolean) = Unit

    override fun hideScrubber(animationDurationMs: Long) = Unit

    private var durationMs: Long = 0
    private var bufferedMs: Long = 0

    override fun setDuration(durationMs: Long) {
        this.durationMs = durationMs
        super.setDuration(durationMs)
    }

    override fun setBufferedPosition(bufferedPosition: Long) {
        bufferedMs = bufferedPosition
        super.setBufferedPosition(bufferedPosition)
    }

    /// Drawn BEFORE super, not over it: what super still draws — the
    /// chapter marks and the scrubber — belongs on top of both of these.
    override fun onDraw(canvas: Canvas) {
        val leftPx = paddingLeft + scrubberPaddingPx
        val rightPx = width - paddingRight - scrubberPaddingPx
        val top = (height - barHeightPx) / 2f
        val bottom = top + barHeightPx
        if (rightPx > leftPx) {
            canvas.drawRect(leftPx.toFloat(), top, rightPx.toFloat(), bottom, basePaint)
            val band = seekWindowBandPx(
                startMs = windowStartMs(),
                endMs = bufferedMs,
                durationMs = durationMs,
                leftPx = leftPx,
                rightPx = rightPx,
            )
            if (band != null) {
                canvas.drawRect(band.first.toFloat(), top, band.last.toFloat(), bottom, bandPaint)
            }
        }
        super.onDraw(canvas)
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density + 0.5f).toInt()
}
