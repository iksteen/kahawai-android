package com.kolktech.kahawai.ui.player

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.provider.Settings
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
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
import com.kolktech.kahawai.R
import com.kolktech.kahawai.data.network.dto.SubtitleTrack
import com.kolktech.kahawai.data.network.dto.displayLabel
import com.kolktech.kahawai.data.repository.PlaybackRepository
import com.kolktech.kahawai.ui.player.subtitle.AssSubtitleOverlay
import com.kolktech.kahawai.ui.player.subtitle.ImageSubtitleOverlay
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
        onDispose {
            controller?.show(WindowInsetsCompat.Type.navigationBars())
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
    data class Seek(val forward: Boolean) : GestureIndicator
    data class Resize(val label: String) : GestureIndicator
}

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

    fun flash(next: GestureIndicator) {
        indicator = next
        hideJob?.cancel()
        hideJob = scope.launch {
            delay(700)
            indicator = null
        }
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
    // of TrackSelectionDialogBuilder's modal AlertDialog.
    fun showAudioTrackMenu(menuContext: Context, anchor: View) {
        val groups = viewModel.player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        if (groups.isEmpty()) return
        val nameProvider = DefaultTrackNameProvider(menuContext.resources)
        PopupMenu(menuContext, anchor).apply {
            val selectionForItem = mutableMapOf<Int, Pair<TrackGroup, Int>?>()
            var itemId = 0
            menu.add(0, itemId, itemId, "Default")
            selectionForItem[itemId] = null
            itemId++
            groups.forEach { group ->
                for (trackIndex in 0 until group.length) {
                    if (!group.isTrackSupported(trackIndex)) continue
                    menu.add(0, itemId, itemId, nameProvider.getTrackName(group.getTrackFormat(trackIndex)))
                    selectionForItem[itemId] = group.mediaTrackGroup to trackIndex
                    itemId++
                }
            }
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
        PopupMenu(menuContext, anchor).apply {
            val trackForItem = mutableMapOf<Int, SubtitleTrack?>()
            var itemId = 0
            menu.add(0, itemId, itemId, "Off")
            trackForItem[itemId] = null
            itemId++
            tracks.forEach { track ->
                val menuItem = menu.add(0, itemId, itemId, track.displayLabel())
                // Burn restarts the session with a forced video encode and
                // "none" can't be served at all — offer them but don't
                // pretend they're a free client-side switch like the rest.
                menuItem.isEnabled = track.delivery != "none"
                trackForItem[itemId] = track
                itemId++
            }
            setOnMenuItemClickListener { item ->
                if (trackForItem.containsKey(item.itemId)) {
                    viewModel.selectSubtitleTrack(trackForItem[item.itemId])
                }
                true
            }
        }.show()
    }

    fun showResizeMenu(menuContext: Context, anchor: View, target: PlayerView) {
        PopupMenu(menuContext, anchor).apply {
            RESIZE_MODES.forEachIndexed { index, (_, label) -> menu.add(0, index, index, label) }
            setOnMenuItemClickListener { item ->
                resizeModeIndex = item.itemId
                target.resizeMode = RESIZE_MODES[resizeModeIndex].first
                flash(GestureIndicator.Resize(RESIZE_MODES[resizeModeIndex].second))
                true
            }
        }.show()
    }

    fun showSpeedMenu(menuContext: Context, anchor: View) {
        PopupMenu(menuContext, anchor).apply {
            PLAYBACK_SPEEDS.forEachIndexed { index, speed -> menu.add(0, index, index, "${speed}x") }
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
    // covering what the native one offered (speed) plus resize mode.
    fun showSettingsMenu(menuContext: Context, anchor: View, target: PlayerView) {
        PopupMenu(menuContext, anchor).apply {
            menu.add(0, 0, 0, "Video: ${RESIZE_MODES[resizeModeIndex].second}")
            menu.add(0, 1, 1, "Playback speed")
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    0 -> showResizeMenu(menuContext, anchor, target)
                    1 -> showSpeedMenu(menuContext, anchor)
                }
                true
            }
        }.show()
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
    val gestureDetector = remember {
        GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                private var startedOnLeftSide = true

                override fun onDown(e: MotionEvent): Boolean {
                    val width = playerView?.width ?: return true
                    startedOnLeftSide = e.x < width / 2f
                    return true
                }

                // PlayerView.performClick() toggles controller visibility
                // itself; routing the tap through it (rather than calling
                // show/hideController by hand) keeps touch and
                // accessibility-service activation on the same code path —
                // a TalkBack double-tap fires performClick() directly and
                // gets identical behavior.
                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    playerView?.performClick()
                    return true
                }

                override fun onDoubleTap(e: MotionEvent): Boolean {
                    if (startedOnLeftSide) {
                        viewModel.player.seekBack()
                        flash(GestureIndicator.Seek(forward = false))
                    } else {
                        viewModel.player.seekForward()
                        flash(GestureIndicator.Seek(forward = true))
                    }
                    return true
                }

                override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                    val height = playerView?.height?.takeIf { it > 0 } ?: return true
                    val delta = distanceY / height
                    if (startedOnLeftSide) {
                        applyBrightness((brightness + delta).coerceIn(0f, 1f))
                    } else {
                        applyVolume((volume + delta).coerceIn(0f, 1f))
                    }
                    return true
                }
            },
        )
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
                    // onSingleTapConfirmed (see above), satisfying the
                    // click-path contract this lint check is about.
                    @Suppress("ClickableViewAccessibility")
                    setOnTouchListener { _, event -> gestureDetector.onTouchEvent(event); true }
                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { visibility ->
                            controllerVisible = visibility == View.VISIBLE
                        },
                    )
                    findViewById<ImageButton>(R.id.exo_audio_track).setOnClickListener { anchor ->
                        showAudioTrackMenu(ctx, anchor)
                    }
                    findViewById<ImageButton>(androidx.media3.ui.R.id.exo_settings).setOnClickListener { anchor ->
                        showSettingsMenu(ctx, anchor, this)
                    }
                    findViewById<ImageButton>(androidx.media3.ui.R.id.exo_subtitle).setOnClickListener { anchor ->
                        showSubtitleTrackMenu(ctx, anchor)
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
                is GestureIndicator.Seek -> if (current.forward) "Forward ⏩" else "⏪ Rewind"
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
