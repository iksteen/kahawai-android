package com.kolktech.kahawai.ui.player

import android.app.Application
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.kolktech.kahawai.data.network.ApiClient
import com.kolktech.kahawai.data.network.dto.StartSessionResponse
import com.kolktech.kahawai.data.network.readableMessage
import com.kolktech.kahawai.data.repository.PlaybackRepository
import com.kolktech.kahawai.playback.CapabilityProfileBuilder

sealed interface PlayerState {
    data object Loading : PlayerState
    data class Error(val message: String) : PlayerState
    data object Ready : PlayerState
}

/// Progress cadence matches the web client (web/src/views/Player.tsx:678-710):
/// every 10s while playing, plus on pause and on teardown.
private const val PROGRESS_INTERVAL_MS = 10_000L

@OptIn(UnstableApi::class)
class PlayerViewModel(
    application: Application,
    private val repo: PlaybackRepository,
    private val itemId: String,
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
            .build()
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

    init {
        realPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) reportProgressNow()
            }
        })
        start()
    }

    private fun start() {
        _state.value = PlayerState.Loading
        viewModelScope.launch {
            try {
                val profile = CapabilityProfileBuilder.build(getApplication())
                val session = repo.startSession(itemId, profile, startMs)
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
                val result = repo.seek(session.sessionId, targetMs)
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
            .build()
        realPlayer.setMediaItem(mediaItem, startPositionMs)
        realPlayer.prepare()
        realPlayer.playWhenReady = true
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
