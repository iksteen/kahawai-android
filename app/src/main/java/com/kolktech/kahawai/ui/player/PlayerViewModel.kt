package com.kolktech.kahawai.ui.player

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.kolktech.kahawai.data.network.ApiClient
import com.kolktech.kahawai.data.network.dto.StartSessionResponse
import com.kolktech.kahawai.data.network.dto.SubtitleTrack
import com.kolktech.kahawai.data.network.readableMessage
import com.kolktech.kahawai.data.repository.PlaybackRepository
import com.kolktech.kahawai.playback.CapabilityProfileBuilder

sealed interface PlayerState {
    data object Loading : PlayerState
    data class Error(val message: String) : PlayerState
    data object Ready : PlayerState
}

/// What an "ass"/"overlay" delivery renderer needs to tap the hub's
/// out-of-band session streams (subs-{id}.ass / subs-{id}.jsonl):
/// [streamBaseUrl] is [session.streamUrl] with the last path segment
/// stripped, mirroring web/src/views/Player.tsx's `base`. [streamBaseUrl]
/// itself doesn't change across a seek-restart within one session (only
/// [offsetMs] does — see PlayerViewModel.attach), so [epoch] is bumped on
/// every attach() to give overlay composables an explicit key to
/// reconnect their stream on.
data class SubtitleSession(
    val streamBaseUrl: String,
    val offsetMs: Long,
    val epoch: Int,
)

/// Progress cadence matches the web client (web/src/views/Player.tsx:678-710):
/// every 10s while playing, plus on pause and on teardown.
private const val PROGRESS_INTERVAL_MS = 10_000L

private const val TAG = "PlayerViewModel"

@OptIn(UnstableApi::class)
class PlayerViewModel(
    application: Application,
    private val repo: PlaybackRepository,
    val itemId: String,
    private val startMs: Long,
) : AndroidViewModel(application) {
    private val _state = MutableStateFlow<PlayerState>(PlayerState.Loading)
    val state: StateFlow<PlayerState> = _state

    private val realPlayer: ExoPlayer by lazy {
        val dataSourceFactory = DefaultDataSource.Factory(
            getApplication(),
            OkHttpDataSource.Factory(ApiClient.authenticatedOkHttpClient()),
        )
        ExoPlayer.Builder(getApplication())
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            // handleAudioFocus=true so ExoPlayer pauses itself on transient
            // audio focus loss (e.g. an incoming call ringing) and resumes
            // when focus is returned, without us wiring up an AudioManager
            // listener by hand.
            .setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus= */ true)
            .build()
            .apply {
                // Text tracks stay enabled but with no override selected —
                // "off" by default. Sideloaded VTT (Text delivery) configs
                // are attached per-MediaItem in attach(); switching between
                // them is a TrackSelectionOverride (selectSubtitleTrack
                // below), same mechanism as showAudioTrackMenu. Ass/Overlay
                // delivery never touches this pipeline at all — those are
                // out-of-band taps rendered by AssSubtitleOverlay /
                // ImageSubtitleOverlay.
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .build()
            }
    }

    /// What the UI (PlayerView / its built-in controller) should hold.
    /// HLS sessions serve a growing EVENT playlist: a seek past what's
    /// been produced needs the hub to restart the pipeline elsewhere
    /// (§6 seek-anywhere, HUB-18), not a raw ExoPlayer seek — this
    /// reroutes the controller's seek/seekForward/seekBack through that
    /// logic transparently, mirroring web/src/views/Player.tsx:633-663.
    /// Unlike the web client we don't check whether the target is
    /// already inside the produced window first; every HLS seek
    /// round-trips through the hub. Simpler, always correct, and only
    /// costs a network round trip on a seek that could sometimes have
    /// been served locally — a fine trade for a first playback pass.
    val player: Player by lazy {
        object : ForwardingPlayer(realPlayer) {
            override fun seekTo(positionMs: Long) = handleSeek(offsetMs + positionMs)
            override fun seekForward() = handleSeek(offsetMs + realPlayer.currentPosition + realPlayer.seekForwardIncrement)
            override fun seekBack() = handleSeek((offsetMs + realPlayer.currentPosition - realPlayer.seekBackIncrement).coerceAtLeast(0))
        }
    }

    private var session: StartSessionResponse? = null
    private var offsetMs: Long = 0
    private var progressJob: Job? = null
    private var subtitleEpoch: Int = 0

    private val _subtitleTracks = MutableStateFlow<List<SubtitleTrack>>(emptyList())
    val subtitleTracks: StateFlow<List<SubtitleTrack>> = _subtitleTracks

    /// null = "Off". Only Text-delivery selection changes ExoPlayer's own
    /// track override; Ass/Overlay delivery is read entirely by
    /// AssSubtitleOverlay/ImageSubtitleOverlay, driven off this + [subtitleSession].
    private val _selectedSubtitleTrack = MutableStateFlow<SubtitleTrack?>(null)
    val selectedSubtitleTrack: StateFlow<SubtitleTrack?> = _selectedSubtitleTrack

    private val _subtitleSession = MutableStateFlow<SubtitleSession?>(null)
    val subtitleSession: StateFlow<SubtitleSession?> = _subtitleSession

    init {
        realPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) reportProgressNow()
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "Playback failed for item=$itemId", error)
                progressJob?.cancel()
                _state.value = PlayerState.Error("Playback failed: ${error.readableMessage()}")
            }

            /// Sideloaded subtitle track groups (attach()'s
            /// setSubtitleConfigurations) don't exist the instant
            /// prepare() returns — they show up here once metadata loads.
            /// Reapplying on every change is what carries a Text-delivery
            /// selection forward across a seek-restart's fresh MediaItem
            /// (a plain post-prepare() call would silently see no groups yet).
            override fun onTracksChanged(tracks: Tracks) {
                applySubtitleTrackSelectionOverride()
            }
        })
        start()
    }

    private fun start() {
        _state.value = PlayerState.Loading
        viewModelScope.launch {
            try {
                val profile = CapabilityProfileBuilder.build(getApplication())
                // Run alongside startSession, not after it — attach() below
                // needs the resolved list to build the sideloaded VTT
                // configs for the FIRST MediaItem. Best-effort: a failure
                // here means no subtitle picker entries, not a broken
                // playback session, so it's swallowed rather than failing
                // the whole start().
                val subtitlesDeferred = async {
                    try {
                        repo.subtitles(itemId)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to load subtitle tracks for item=$itemId", e)
                        emptyList()
                    }
                }
                val session = repo.startSession(itemId, profile, startMs, subtitleTrack = _selectedSubtitleTrack.value?.id)
                _subtitleTracks.value = subtitlesDeferred.await()
                this@PlayerViewModel.session = session
                offsetMs = session.partBaseMs ?: 0
                attach(session, startPositionMs = (startMs - offsetMs).coerceAtLeast(0))
                _state.value = PlayerState.Ready
                startProgressLoop()
            } catch (e: Exception) {
                _state.value = PlayerState.Error(e.readableMessage())
            }
        }
    }

    private fun handleSeek(targetMs: Long) {
        val session = session ?: return
        if (session.mode == "direct") {
            realPlayer.seekTo(targetMs)
            return
        }
        viewModelScope.launch {
            try {
                realPlayer.pause()
                val result = repo.seek(session.sessionId, targetMs, subtitleTrack = _selectedSubtitleTrack.value?.id)
                offsetMs = result.partBaseMs ?: 0
                attach(session, startPositionMs = (targetMs - offsetMs).coerceAtLeast(0))
            } catch (e: Exception) {
                _state.value = PlayerState.Error("Seek failed: ${e.readableMessage()}")
            }
        }
    }

    private fun attach(session: StartSessionResponse, startPositionMs: Long) {
        val uri = ApiClient.baseUrl().trimEnd('/') + session.streamUrl
        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .setMimeType(session.contentType)
            .setSubtitleConfigurations(textDeliverySubtitleConfigs())
            .build()
        realPlayer.setMediaItem(mediaItem, startPositionMs)
        realPlayer.prepare()
        realPlayer.playWhenReady = true
        // Track groups for the just-set MediaItem don't exist yet (they
        // load asynchronously) — the onTracksChanged listener in init{}
        // reapplies the pinned selection once they do. Nothing to do here
        // beyond that; a stale override from the PREVIOUS MediaItem can't
        // linger since setMediaItem/prepare drops it.
        // uri's last path segment is the playlist/manifest file
        // (master.m3u8 for remux/transcode); the session's out-of-band
        // taps (subs-{id}.ass / subs-{id}.jsonl) live alongside it. epoch
        // bumps on every attach() (initial + every seek-restart) so
        // AssSubtitleOverlay/ImageSubtitleOverlay know to reconnect even
        // though streamBaseUrl itself doesn't change within one session.
        subtitleEpoch++
        _subtitleSession.value = SubtitleSession(
            streamBaseUrl = uri.substringBeforeLast('/') + "/",
            offsetMs = offsetMs,
            epoch = subtitleEpoch,
        )
    }

    /// Media3 can't attach a new [MediaItem.SubtitleConfiguration] to an
    /// already-prepared MediaItem, so every Text-delivery track is
    /// sideloaded here upfront; switching between them is then a
    /// TrackSelectionOverride (applySubtitleTrackSelectionOverride) with
    /// no re-prepare. `shift_ms` is recomputed every attach() call since
    /// offsetMs changes on every seek-restart.
    private fun textDeliverySubtitleConfigs(): List<MediaItem.SubtitleConfiguration> =
        _subtitleTracks.value
            .filter { it.delivery == "text" }
            .map { track ->
                val vttUrl = "${ApiClient.baseUrl().trimEnd('/')}/api/v1/items/$itemId/subtitles/${track.id}.vtt?shift_ms=${-offsetMs}"
                MediaItem.SubtitleConfiguration.Builder(Uri.parse(vttUrl))
                    .setMimeType(MimeTypes.TEXT_VTT)
                    .setLanguage(track.language)
                    .setId(track.id.toString())
                    .build()
            }

    /// Reapplies the current [selectedSubtitleTrack] as a
    /// TrackSelectionOverride against whatever sideloaded text tracks the
    /// just-rebuilt MediaItem carries. A no-op (override cleared) unless
    /// the selection is itself Text delivery — Ass/Overlay tracks are
    /// never part of ExoPlayer's own track groups.
    private fun applySubtitleTrackSelectionOverride() {
        val track = _selectedSubtitleTrack.value
        val params = realPlayer.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
        if (track != null && track.delivery == "text") {
            val group = realPlayer.currentTracks.groups
                .firstOrNull { it.type == C.TRACK_TYPE_TEXT && it.mediaTrackGroup.getFormat(0).id == track.id.toString() }
            if (group != null) params.addOverride(TrackSelectionOverride(group.mediaTrackGroup, 0))
        }
        realPlayer.trackSelectionParameters = params.build()
    }

    /// null clears selection ("Off"). Text-delivery switches are instant
    /// (TrackSelectionOverride over already-sideloaded VTT configs, no
    /// re-prepare); Ass/Overlay delivery is picked up by
    /// AssSubtitleOverlay/ImageSubtitleOverlay observing [selectedSubtitleTrack]
    /// + [subtitleSession] — neither needs a session restart, matching the
    /// hub's own rule that a text/ass/downloaded pick has no plan impact
    /// (the client fetches those itself). The id rides along on the next
    /// natural startSession/seek call for the hub's own bookkeeping.
    fun selectSubtitleTrack(track: SubtitleTrack?) {
        _selectedSubtitleTrack.value = track
        // Text delivery lives on tracks attached at the START of the
        // CURRENT MediaItem (realPlayer.currentTracks), so this doesn't
        // need a fresh attach() — unlike the initial sideload list itself.
        applySubtitleTrackSelectionOverride()
    }

    private fun startProgressLoop() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (isActive) {
                delay(PROGRESS_INTERVAL_MS)
                if (realPlayer.isPlaying) reportProgressNow()
            }
        }
    }

    private fun reportProgressNow() {
        val session = session ?: return
        val positionMs = offsetMs + realPlayer.currentPosition
        viewModelScope.launch {
            try {
                repo.reportProgress(session.sessionId, positionMs)
            } catch (e: Exception) {
                // Best-effort, like the web client's beforeunload report.
            }
        }
    }

    /// The ViewModel's own scope is cancelled around the same time
    /// onCleared() runs, which isn't a reliable place to await a network
    /// call — this cleanup is best-effort background work, same as the
    /// web client's `beforeunload` handler (web/src/views/Player.tsx:695-699).
    override fun onCleared() {
        val session = session
        val finalPositionMs = if (session != null) offsetMs + realPlayer.currentPosition else null
        realPlayer.release()
        if (session != null && finalPositionMs != null) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    repo.reportProgress(session.sessionId, finalPositionMs)
                    repo.endSession(session.sessionId)
                } catch (e: Exception) {
                    // Nothing left to recover to; the hub's own session
                    // reaper is the backstop.
                }
            }
        }
        super.onCleared()
    }
}
