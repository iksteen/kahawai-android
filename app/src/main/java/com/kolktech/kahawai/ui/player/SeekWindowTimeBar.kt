@file:OptIn(UnstableApi::class)

package com.kolktech.kahawai.ui.player

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.TimeBar
import com.kolktech.kahawai.R

/// Per-press D-pad seek step, in ms, for a key held for [repeatCount]
/// system auto-repeats (0 on the initial, un-repeated press). Tiered
/// rather than a smooth curve so a viewer feels a small number of
/// distinct "gears" instead of a jump size that's already hard to predict
/// a few hundred ms into holding the key down.
///
/// A bare tap needs to move the playhead by about a second - fine enough
/// to land on a specific frame - while holding the key has to cross a
/// two-hour movie in a few seconds, which a flat per-tick amount can't do
/// on its own: the system's key-repeat rate (roughly one event per 50ms)
/// already multiplies whatever's returned here, so each tier's ms/press
/// compounds into a much larger ms/second once repeats are flowing.
internal fun leanbackSeekIncrementMs(repeatCount: Int): Long = when {
    repeatCount < 10 -> 1_000L
    repeatCount < 30 -> 5_000L
    repeatCount < 60 -> 15_000L
    else -> 30_000L
}

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

    /// Tracks DefaultTimeBar's own (private) scrubbing flag via the same
    /// listener hook it notifies PlayerControlView through, so Back can
    /// tell whether there's a pending seek to cancel.
    private var scrubbing = false

    init {
        addListener(
            object : TimeBar.OnScrubListener {
                override fun onScrubStart(timeBar: TimeBar, position: Long) {
                    scrubbing = true
                }

                override fun onScrubMove(timeBar: TimeBar, position: Long) = Unit

                override fun onScrubStop(timeBar: TimeBar, position: Long, canceled: Boolean) {
                    scrubbing = false
                }
            },
        )
    }

    /// DefaultTimeBar's own D-pad handling (which this defers straight to
    /// below) reads a single fixed key-time-increment for every press -
    /// left unset, that default is duration/20, a step that scales with
    /// the title instead of the clock and lands around a full minute per
    /// press on anything longer than a few minutes. Setting the increment
    /// here, keyed to how many auto-repeats the held key has produced so
    /// far, turns that one fixed step into the tiered ramp from
    /// [leanbackSeekIncrementMs]: a lone tap nudges by a second, holding
    /// the key ramps up through it.
    ///
    /// Back gets special handling while a seek is pending: DefaultTimeBar
    /// itself has no notion of "cancel" from the keyboard - only OK/Enter
    /// (which commits) and losing focus, which also commits, silently
    /// landing Back on the scrubbed-to position instead of leaving
    /// playback where it was. Disabling and re-enabling the bar is the
    /// only public surface that reaches its private cancel path (see
    /// DefaultTimeBar.setEnabled, which stops scrubbing with canceled=true
    /// when disabled mid-scrub) - both calls land within this one key
    /// event, so there's no visible disabled flash. A cancelled stop never
    /// seeks the player (see PlayerControlView's onScrubStop), so nothing
    /// needs to be un-seeked; the bar's own displayed position resyncs on
    /// PlayerControlView's next progress tick, which a non-scrubbing state
    /// now lets through again. The key is consumed so Back cancels the
    /// seek on its own press rather than also closing the player behind
    /// it.
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && scrubbing) {
            isEnabled = false
            isEnabled = true
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            setKeyTimeIncrement(leanbackSeekIncrementMs(event.repeatCount))
        }
        return super.onKeyDown(keyCode, event)
    }

    /// DefaultTimeBar schedules exactly one delayed callback anywhere in
    /// its implementation: a 1s "stop scrubbing" timer re-armed on every
    /// D-pad press, which auto-commits the pending seek once the key goes
    /// quiet for a second - racing OK/Back for the same decision this bar
    /// otherwise leaves to them explicitly. There's no public handle on
    /// that callback to cancel just it, so this drops every 1s post this
    /// view is asked to schedule; if a future DefaultTimeBar adds another
    /// legitimate one, it would silently stop firing too.
    override fun postDelayed(action: Runnable, delayMillis: Long): Boolean {
        if (delayMillis == 1_000L) return true
        return super.postDelayed(action, delayMillis)
    }

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
