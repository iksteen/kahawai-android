package com.kolktech.kahawai.ui.player

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.PopupMenu
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.C
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.DefaultTrackNameProvider
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import com.kolktech.kahawai.R
import com.kolktech.kahawai.data.network.dto.SubtitleTrack
import com.kolktech.kahawai.data.network.dto.displayLabel
import com.kolktech.kahawai.data.repository.PlaybackRepository
import com.kolktech.kahawai.ui.player.subtitle.AssSubtitleOverlay
import com.kolktech.kahawai.ui.player.subtitle.ImageSubtitleOverlay
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private val RESIZE_MODES = listOf(
    AspectRatioFrameLayout.RESIZE_MODE_FIT to "Fit",
    AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "Crop",
    AspectRatioFrameLayout.RESIZE_MODE_FILL to "Stretch",
)

private val PLAYBACK_SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun PlayerScreen(
    itemId: String,
    startMs: Long,
    onClose: () -> Unit,
    initialAudioTrack: Int = 0,
    initialSubtitleTrackId: Long? = null,
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val repo = remember { PlaybackRepository() }
    val viewModel: PlayerViewModel = viewModel(
        key = itemId,
        factory = viewModelFactory {
            initializer { PlayerViewModel(application, repo, itemId, startMs, initialAudioTrack, initialSubtitleTrackId) }
        },
    )
    val state by viewModel.state.collectAsState()

    BackHandler(onBack = onClose)

    // True fullscreen for the whole player screen. The status bar is
    // already hidden app-wide (MainActivity.hideStatusBar), so only the
    // navigation bar needs taking away here — and, crucially, only the
    // navigation bar may be restored on the way out, or leaving the
    // player would bring the status bar back for the rest of the app.
    // The root NavHost's safeDrawingPadding() shrinks to match
    // automatically once the bar reports zero inset, so no other screen
    // needs to know about this.
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowInsetsControllerCompat(it, view) }
        controller?.hide(WindowInsetsCompat.Type.navigationBars())
        // Draw into the camera-cutout area too: with the bars hidden the
        // system otherwise letterboxes the window at the cutout's edge
        // (black band beside the notch in landscape). SHORT_EDGES covers
        // camera cutouts in both orientations — they always sit on a
        // short edge of the panel. Window-level and player-only, restored
        // on the way out — the rest of the app keeps its
        // safeDrawingPadding (see KahawaiNavGraph), which the player
        // route skips.
        val previousCutoutMode =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && window != null) {
                val previous = window.attributes.layoutInDisplayCutoutMode
                window.attributes = window.attributes.apply {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
                previous
            } else {
                null
            }
        onDispose {
            controller?.show(WindowInsetsCompat.Type.navigationBars())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && window != null && previousCutoutMode != null) {
                window.attributes = window.attributes.apply { layoutInDisplayCutoutMode = previousCutoutMode }
            }
        }
    }

    // Pause the moment the app stops being visible: home button, app
    // switcher, an incoming call's full-screen UI, etc. Rotating the
    // device also fires ON_STOP (Android tears the activity down and
    // recreates it for the config change) but that's not "backgrounded" -
    // isChangingConfigurations tells the two apart so a rotation doesn't
    // pause playback. A call that doesn't take over the screen (just
    // rings) is instead caught by ExoPlayer's own audio-focus handling
    // (see PlayerViewModel).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && context.findActivity()?.isChangingConfigurations != true) {
                viewModel.player.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        when (val s = state) {
            is PlayerState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            is PlayerState.Error -> Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(s.message, color = MaterialTheme.colorScheme.error)
                Button(onClick = onClose, modifier = Modifier.padding(top = 12.dp)) {
                    Text("Back")
                }
            }
            is PlayerState.Ready -> PlayerContent(viewModel, onClose)
        }
    }
}

private sealed interface GestureIndicator {
    data class Brightness(val value: Float) : GestureIndicator
    data class Volume(val value: Float) : GestureIndicator
    data class Seek(val forward: Boolean, val seconds: Float) : GestureIndicator
    data class Resize(val label: String) : GestureIndicator
}

private enum class DragMode { NONE, BRIGHTNESS, VOLUME }

/// Mutable gesture bookkeeping shared between the tap/scroll detector,
/// the pinch detector and the raw touch listener. Lives in a plain class
/// (remembered once) rather than Compose state — nothing here drives
/// recomposition, it only sequences events within a single gesture.
private class TouchState {
    var dragMode = DragMode.NONE
    var pinchActive = false
    var pinchScale = 1f

    // Double-tap-to-seek chain: the first double tap seeks 5s, every
    // further quick tap grows the same chain by 2.5s (2 taps = 5s,
    // 6 taps = 15s). Targets are computed from the position captured at
    // chain start, not currentPosition, because each hub seek is an async
    // round trip that supersedes the previous one — reading
    // currentPosition mid-chain would see a position no tap has landed
    // on yet.
    var seekChainMs = 0L
    var seekChainBasisMs = 0L
    var seekForward = true
    var lastSeekTapTime = 0L

    var lastTapUpTime = 0L
    var pendingControllerToggle: Job? = null
}

private const val SEEK_FIRST_TAP_MS = 5_000L
private const val SEEK_EXTRA_TAP_MS = 2_500L

/// How long after the last seek-tap another tap still extends the chain
/// (also how long the seek indicator stays up, so the indicator being
/// visible == "another tap will add to the seek").
private const val SEEK_CHAIN_WINDOW_MS = 800L

@OptIn(UnstableApi::class)
@Composable
private fun PlayerContent(viewModel: PlayerViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val maxVolume = remember(audioManager) {
        audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    }

    var brightness by remember {
        mutableFloatStateOf(
            activity?.window?.attributes?.screenBrightness?.takeIf { it in 0f..1f }
                ?: (Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f),
        )
    }
    var volume by remember {
        mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) / maxVolume.toFloat())
    }
    var indicator by remember { mutableStateOf<GestureIndicator?>(null) }
    var hideJob by remember { mutableStateOf<Job?>(null) }
    var playerView by remember { mutableStateOf<PlayerView?>(null) }
    var resizeModeIndex by remember { mutableIntStateOf(0) }
    var controllerVisible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val selectedSubtitle by viewModel.selectedSubtitleTrack.collectAsState()
    val subtitleSession by viewModel.subtitleSession.collectAsState()
    val transientError by viewModel.transientError.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Recoverable failures (failed seek, failed subtitle switch) — the
    // stream is still playing, so they get a snackbar here rather than
    // the terminal PlayerState.Error screen.
    LaunchedEffect(transientError) {
        transientError?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearTransientError()
        }
    }
    // Ass/Overlay delivery need their own font/session-tap fetches;
    // PlaybackRepository holds no state, so a fresh instance here (rather
    // than threading the one PlayerScreen built for the ViewModel) keeps
    // this composable self-contained.
    val subtitleRepo = remember { PlaybackRepository() }

    fun flash(next: GestureIndicator, durationMs: Long = 700) {
        indicator = next
        hideJob?.cancel()
        hideJob = scope.launch {
            delay(durationMs)
            indicator = null
        }
    }

    fun applyResize(index: Int) {
        resizeModeIndex = index
        playerView?.resizeMode = RESIZE_MODES[index].first
    }

    fun applyBrightness(value: Float) {
        brightness = value
        activity?.window?.let { window -> window.attributes = window.attributes.apply { screenBrightness = value } }
        flash(GestureIndicator.Brightness(value))
    }

    fun applyVolume(value: Float) {
        volume = value
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (value * maxVolume).roundToInt(), 0)
        flash(GestureIndicator.Volume(value))
    }

    // Anchored popup, same style as the resize/speed menus below, instead
    // of TrackSelectionDialogBuilder's modal AlertDialog. Reached through
    // the settings gear menu — portrait screens don't have bottom-bar
    // width for a dedicated audio button.
    fun showAudioTrackMenu(menuContext: Context, anchor: View) {
        val groups = viewModel.player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        if (groups.isEmpty()) return
        val nameProvider = DefaultTrackNameProvider(menuContext.resources)
        val overrides = viewModel.player.trackSelectionParameters.overrides
        val hasAudioOverride = overrides.values.any { it.type == C.TRACK_TYPE_AUDIO }
        PopupMenu(menuContext, anchor).apply {
            val selectionForItem = mutableMapOf<Int, Pair<TrackGroup, Int>?>()
            var itemId = 0
            var checkedItemId = if (hasAudioOverride) -1 else 0
            menu.add(0, itemId, itemId, "Default")
            selectionForItem[itemId] = null
            itemId++
            groups.forEach { group ->
                for (trackIndex in 0 until group.length) {
                    if (!group.isTrackSupported(trackIndex)) continue
                    menu.add(0, itemId, itemId, nameProvider.getTrackName(group.getTrackFormat(trackIndex)))
                    selectionForItem[itemId] = group.mediaTrackGroup to trackIndex
                    if (overrides[group.mediaTrackGroup]?.trackIndices?.contains(trackIndex) == true) {
                        checkedItemId = itemId
                    }
                    itemId++
                }
            }
            menu.setGroupCheckable(0, true, true)
            menu.findItem(checkedItemId)?.isChecked = true
            setOnMenuItemClickListener { item ->
                val selection = selectionForItem[item.itemId]
                val params = viewModel.player.trackSelectionParameters.buildUpon()
                    .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                if (selection != null) params.addOverride(TrackSelectionOverride(selection.first, selection.second))
                viewModel.player.trackSelectionParameters = params.build()
                true
            }
        }.show()
    }

    // Sourced from the hub's own track+delivery list (viewModel.subtitleTracks),
    // not player.currentTracks.groups — Ass/Overlay delivery never
    // becomes an ExoPlayer text track at all, only Text delivery does.
    fun showSubtitleTrackMenu(menuContext: Context, anchor: View) {
        val tracks = viewModel.subtitleTracks.value
        val selected = viewModel.selectedSubtitleTrack.value
        PopupMenu(menuContext, anchor).apply {
            val trackForItem = mutableMapOf<Int, SubtitleTrack?>()
            var itemId = 0
            var checkedItemId = if (selected == null) 0 else -1
            menu.add(0, itemId, itemId, "Off")
            trackForItem[itemId] = null
            itemId++
            tracks.forEach { track ->
                val menuItem = menu.add(0, itemId, itemId, track.displayLabel())
                // Burn restarts the session with a forced video encode and
                // "none" can't be served at all — offer them but don't
                // pretend they're a free client-side switch like the rest.
                menuItem.isEnabled = track.delivery != "none"
                if (track.id == selected?.id) checkedItemId = itemId
                trackForItem[itemId] = track
                itemId++
            }
            menu.setGroupCheckable(0, true, true)
            menu.findItem(checkedItemId)?.isChecked = true
            setOnMenuItemClickListener { item ->
                if (trackForItem.containsKey(item.itemId)) {
                    viewModel.selectSubtitleTrack(trackForItem[item.itemId])
                }
                true
            }
        }.show()
    }

    fun showResizeMenu(menuContext: Context, anchor: View) {
        PopupMenu(menuContext, anchor).apply {
            RESIZE_MODES.forEachIndexed { index, (_, label) -> menu.add(0, index, index, label) }
            menu.setGroupCheckable(0, true, true)
            menu.findItem(resizeModeIndex)?.isChecked = true
            setOnMenuItemClickListener { item ->
                applyResize(item.itemId)
                flash(GestureIndicator.Resize(RESIZE_MODES[item.itemId].second))
                true
            }
        }.show()
    }

    fun showSpeedMenu(menuContext: Context, anchor: View) {
        PopupMenu(menuContext, anchor).apply {
            PLAYBACK_SPEEDS.forEachIndexed { index, speed -> menu.add(0, index, index, "${speed}x") }
            menu.setGroupCheckable(0, true, true)
            PLAYBACK_SPEEDS.indexOfFirst { it == viewModel.player.playbackParameters.speed }
                .takeIf { it >= 0 }
                ?.let { menu.findItem(it)?.isChecked = true }
            setOnMenuItemClickListener { item ->
                viewModel.player.setPlaybackSpeed(PLAYBACK_SPEEDS[item.itemId])
                true
            }
        }.show()
    }

    // Media3's settings gear menu (speed / audio track) is built by a
    // private, non-extensible adapter inside PlayerControlView, so there's
    // no public way to add a "video aspect" row into it. We take over the
    // gear button's click entirely and show our own two-level menu instead,
    // covering what the native one offered (speed / audio track) plus
    // resize mode. Each row shows its current value so the state is
    // visible without opening the submenu.
    fun showSettingsMenu(menuContext: Context, anchor: View) {
        val speed = viewModel.player.playbackParameters.speed
        val nameProvider = DefaultTrackNameProvider(menuContext.resources)
        val audioGroups = viewModel.player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        val currentAudio = audioGroups.firstNotNullOfOrNull { group ->
            (0 until group.length).firstOrNull { group.isTrackSelected(it) }
                ?.let { nameProvider.getTrackName(group.getTrackFormat(it)) }
        }
        PopupMenu(menuContext, anchor).apply {
            menu.add(0, 0, 0, "Video: ${RESIZE_MODES[resizeModeIndex].second}")
            menu.add(0, 1, 1, "Speed: ${speed}x")
            if (audioGroups.isNotEmpty()) menu.add(0, 2, 2, "Audio: ${currentAudio ?: "Default"}")
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    0 -> showResizeMenu(menuContext, anchor)
                    1 -> showSpeedMenu(menuContext, anchor)
                    2 -> showAudioTrackMenu(menuContext, anchor)
                }
                true
            }
        }.show()
    }

    val touchState = remember { TouchState() }
    val doubleTapTimeoutMs = remember { ViewConfiguration.getDoubleTapTimeout().toLong() }
    val density = context.resources.displayMetrics.density
    // Vertical travel required before a swipe starts adjusting anything —
    // well above touch slop, so the incidental jitter of a tap or the
    // start of a system back/home gesture never registers as a swipe.
    val dragStartThresholdPx = remember { 36 * density }
    // Dead zone along every screen edge: left/right are Android's
    // gesture-navigation back zones, bottom is the home gesture. Swipes
    // beginning there belong to the system, not to brightness/volume.
    val edgeExclusionPx = remember { 32 * density }

    // Seeks issued per tap are safe to fire eagerly: PlayerViewModel's
    // handleSeek cancels the in-flight hub round trip when a newer seek
    // supersedes it, so only the final chain target actually lands.
    fun performChainSeek(state: TouchState) {
        val duration = viewModel.player.duration.takeIf { it != C.TIME_UNSET }
        val target = if (state.seekForward) {
            (state.seekChainBasisMs + state.seekChainMs).let { t -> duration?.let { minOf(t, it) } ?: t }
        } else {
            (state.seekChainBasisMs - state.seekChainMs).coerceAtLeast(0)
        }
        viewModel.player.seekTo(target)
        flash(GestureIndicator.Seek(state.seekForward, state.seekChainMs / 1000f), durationMs = SEEK_CHAIN_WINDOW_MS)
    }

    // A single GestureDetector, built once, attached directly to the
    // PlayerView via setOnTouchListener rather than a Compose overlay
    // drawn on top of it. A transparent Compose sibling covering the
    // whole screen would win every hit test and swallow taps meant for
    // the native play/pause/CC/settings buttons underneath; a listener on
    // the PlayerView itself only sees touches its child buttons didn't
    // already consume, so those buttons keep working. Built once (not
    // per-recomposition) so an in-progress drag doesn't get handed to a
    // brand new detector mid-gesture.
    //
    // Double taps are detected manually from onSingleTapUp timing rather
    // than onDoubleTap: once a seek chain is running, every further tap
    // must extend it immediately, but GestureDetector would only report
    // every second tap (it pairs taps back into fresh double-tap
    // gestures) and would delay them through its own timeout.
    val gestureDetector = remember {
        GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean {
                    touchState.dragMode = DragMode.NONE
                    touchState.pinchActive = false
                    return true
                }

                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    val view = playerView ?: return true
                    val now = e.eventTime
                    val forward = e.x >= view.width / 2f

                    // Tap while the seek indicator is still up: extend the
                    // running chain (or flip it if the side changed).
                    if (touchState.seekChainMs > 0 && now - touchState.lastSeekTapTime <= SEEK_CHAIN_WINDOW_MS) {
                        if (forward != touchState.seekForward) {
                            touchState.seekForward = forward
                            touchState.seekChainMs = SEEK_FIRST_TAP_MS
                            touchState.seekChainBasisMs = viewModel.player.currentPosition
                        } else {
                            touchState.seekChainMs += SEEK_EXTRA_TAP_MS
                        }
                        touchState.lastSeekTapTime = now
                        performChainSeek(touchState)
                        return true
                    }
                    touchState.seekChainMs = 0

                    // Second tap in quick succession: start a seek chain.
                    if (now - touchState.lastTapUpTime <= doubleTapTimeoutMs) {
                        touchState.pendingControllerToggle?.cancel()
                        touchState.pendingControllerToggle = null
                        touchState.lastTapUpTime = 0
                        touchState.seekForward = forward
                        touchState.seekChainMs = SEEK_FIRST_TAP_MS
                        touchState.seekChainBasisMs = viewModel.player.currentPosition
                        touchState.lastSeekTapTime = now
                        performChainSeek(touchState)
                        return true
                    }

                    // Lone tap: toggle the controller, but only once the
                    // double-tap window has passed without a second tap.
                    // PlayerView.performClick() toggles controller
                    // visibility itself; routing the tap through it keeps
                    // touch and accessibility-service activation on the
                    // same code path — a TalkBack double-tap fires
                    // performClick() directly and gets identical behavior.
                    touchState.lastTapUpTime = now
                    touchState.pendingControllerToggle?.cancel()
                    touchState.pendingControllerToggle = scope.launch {
                        delay(doubleTapTimeoutMs)
                        playerView?.performClick()
                    }
                    return true
                }

                override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                    val view = playerView ?: return true
                    val start = e1 ?: return true
                    val height = view.height.takeIf { it > 0 } ?: return true
                    if (touchState.pinchActive || e2.pointerCount > 1) return true

                    if (touchState.dragMode == DragMode.NONE) {
                        if (start.x < edgeExclusionPx || start.x > view.width - edgeExclusionPx ||
                            start.y < edgeExclusionPx || start.y > height - edgeExclusionPx
                        ) {
                            return true
                        }
                        val totalDx = e2.x - start.x
                        val totalDy = e2.y - start.y
                        // Engage only once the finger has clearly committed
                        // to a vertical swipe; anything shorter or more
                        // horizontal is a tap wobble or a system gesture.
                        if (abs(totalDy) < dragStartThresholdPx || abs(totalDy) < abs(totalDx)) return true
                        touchState.dragMode = if (start.x < view.width / 2f) DragMode.BRIGHTNESS else DragMode.VOLUME
                        return true
                    }

                    val delta = distanceY / height
                    when (touchState.dragMode) {
                        DragMode.BRIGHTNESS -> applyBrightness((brightness + delta).coerceIn(0f, 1f))
                        DragMode.VOLUME -> applyVolume((volume + delta).coerceIn(0f, 1f))
                        DragMode.NONE -> {}
                    }
                    return true
                }
            },
        ).apply {
            // SimpleOnGestureListener registers itself as an
            // OnDoubleTapListener, and once GestureDetector classifies a
            // second tap as a double tap it swallows that tap's
            // onSingleTapUp — which would make the manual tap counting
            // above miss every second tap. Unregistering turns the
            // detector's own double-tap tracking off entirely so
            // onSingleTapUp fires for every tap.
            setOnDoubleTapListener(null)
        }
    }

    // Pinch out anywhere on the video zooms it to fill the screen (crop),
    // pinch in returns it to the normal fit. Runs alongside the tap/swipe
    // detector; pinchActive keeps a finished pinch from being misread as
    // a brightness/volume swipe for the rest of the gesture.
    val scaleGestureDetector = remember {
        ScaleGestureDetector(
            context,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                    touchState.pinchActive = true
                    touchState.pinchScale = 1f
                    return true
                }

                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    touchState.pinchScale *= detector.scaleFactor
                    if (touchState.pinchScale >= 1.2f && resizeModeIndex != 1) {
                        applyResize(1)
                        flash(GestureIndicator.Resize("Zoomed to fill"))
                    } else if (touchState.pinchScale <= 0.8f && resizeModeIndex != 0) {
                        applyResize(0)
                        flash(GestureIndicator.Resize("Fit"))
                    }
                    return true
                }
            },
        ).apply {
            // Quick scale (double-tap-then-drag zoom) reuses exactly the
            // tap rhythm the seek chain is built on — a double tap whose
            // second tap drags slightly would start "scaling" and fight
            // both the seek and the swipe gestures.
            isQuickScaleEnabled = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                (LayoutInflater.from(ctx).inflate(R.layout.kw_player_view, null) as PlayerView).apply {
                    player = viewModel.player
                    useController = true
                    // PlayerView's buffering spinner (exo_buffering) exists
                    // in its layout but is off by default — a seek-restart
                    // can legitimately take several seconds (the hub tears
                    // down and restarts its whole remux pipeline at the new
                    // position) with nothing but a black frame on screen to
                    // show for it otherwise, indistinguishable from actually
                    // being stuck.
                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                    // Controls (including our back button, which mirrors
                    // this same visibility) start hidden and only appear
                    // once the viewer taps - not automatically whenever
                    // playback starts/pauses/ends.
                    controllerAutoShow = false
                    hideController()
                    resizeMode = RESIZE_MODES[resizeModeIndex].first
                    // Taps reach performClick via the gesture detector's
                    // onSingleTapUp (see above), satisfying the
                    // click-path contract this lint check is about.
                    @Suppress("ClickableViewAccessibility")
                    setOnTouchListener { _, event ->
                        scaleGestureDetector.onTouchEvent(event)
                        gestureDetector.onTouchEvent(event)
                        true
                    }
                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { visibility ->
                            controllerVisible = visibility == View.VISIBLE
                        },
                    )
                    findViewById<ImageButton>(androidx.media3.ui.R.id.exo_settings).setOnClickListener { anchor ->
                        showSettingsMenu(ctx, anchor)
                    }
                    findViewById<ImageButton>(androidx.media3.ui.R.id.exo_subtitle).setOnClickListener { anchor ->
                        showSubtitleTrackMenu(ctx, anchor)
                    }
                    // SubtitleView ("text" delivery) lives inside
                    // exo_content_frame, which Zoom(crop) scales past the
                    // screen edges — cues keep their bottom padding
                    // relative to the OVERFLOWING frame and land below the
                    // visible screen. Re-derive the padding so cues sit
                    // the default fraction above the *visible* bottom edge
                    // instead; hooked on the frame's layout since both
                    // resize-mode switches and video-size changes relayout
                    // it. (The Ass/Image overlays handle the same problem
                    // themselves via shiftBottomOverflowIntoView.)
                    findViewById<View>(androidx.media3.ui.R.id.exo_content_frame)
                        .addOnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
                            val frameH = (bottom - top).toFloat()
                            val visibleH = height.toFloat()
                            if (frameH <= 0f || visibleH <= 0f) return@addOnLayoutChangeListener
                            val default = SubtitleView.DEFAULT_BOTTOM_PADDING_FRACTION
                            val fraction = if (frameH > visibleH) {
                                // The frame overflows equally top and
                                // bottom (it's centered), so cue bottoms
                                // must clear half the overflow plus the
                                // default margin of the visible height.
                                ((frameH - visibleH) / 2f + default * visibleH) / frameH
                            } else {
                                default
                            }
                            subtitleView?.setBottomPaddingFraction(fraction)
                        }
                    playerView = this
                }
            },
        )

        val activeSession = subtitleSession
        if (activeSession != null) {
            when (selectedSubtitle?.delivery) {
                "overlay" -> ImageSubtitleOverlay(
                    player = viewModel.player,
                    track = selectedSubtitle!!,
                    subtitleSession = activeSession,
                    resizeMode = RESIZE_MODES[resizeModeIndex].first,
                    modifier = Modifier.fillMaxSize(),
                )
                "ass" -> AssSubtitleOverlay(
                    player = viewModel.player,
                    itemId = viewModel.itemId,
                    repo = subtitleRepo,
                    track = selectedSubtitle!!,
                    subtitleSession = activeSession,
                    resizeMode = RESIZE_MODES[resizeModeIndex].first,
                    modifier = Modifier.fillMaxSize(),
                )
                // "text" renders natively via PlayerView's own subtitle
                // view (sideloaded VTT + TrackSelectionOverride); "burn"/
                // "none"/null need no client-side overlay at all.
                else -> {}
            }
        }

        if (controllerVisible) {
            Surface(
                onClick = onClose,
                modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.5f),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier.padding(10.dp),
                    tint = Color.White,
                )
            }
        }

        indicator?.let { current ->
            val label = when (current) {
                is GestureIndicator.Brightness -> "Brightness ${(current.value * 100).roundToInt()}%"
                is GestureIndicator.Volume -> "Volume ${(current.value * 100).roundToInt()}%"
                is GestureIndicator.Seek -> {
                    val secs = if (current.seconds % 1f == 0f) current.seconds.toInt().toString() else current.seconds.toString()
                    if (current.forward) "${secs}s ⏩" else "⏪ ${secs}s"
                }
                is GestureIndicator.Resize -> current.label
            }
            Surface(
                modifier = Modifier.align(Alignment.Center),
                shape = MaterialTheme.shapes.medium,
                color = Color.Black.copy(alpha = 0.6f),
            ) {
                Text(
                    label,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = Color.White,
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
        )
    }
}
