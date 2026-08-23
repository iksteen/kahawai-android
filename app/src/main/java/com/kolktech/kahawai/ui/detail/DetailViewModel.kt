package com.kolktech.kahawai.ui.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.kolktech.kahawai.data.network.dto.Item
import com.kolktech.kahawai.data.network.dto.ItemDetail
import com.kolktech.kahawai.data.network.dto.SubtitleTrack
import com.kolktech.kahawai.data.network.isAuthError
import com.kolktech.kahawai.data.network.readableMessage
import com.kolktech.kahawai.data.repository.CatalogRepository
import com.kolktech.kahawai.playback.CapabilityProfileBuilder

/// Containers with no media of their own — you drill into a child
/// (episode/track) to get a Play button.
internal val NOT_DIRECTLY_PLAYABLE = setOf("show", "album")

sealed interface DetailState {
    data object Loading : DetailState
    data class Error(val message: String, val isAuthError: Boolean = false) : DetailState
    data class Loaded(
        val detail: ItemDetail,
        val children: List<Item>,
        val subtitleTracks: List<SubtitleTrack> = emptyList(),
        val selectedSubtitleTrack: SubtitleTrack? = null,
        val selectedAudioTrackIndex: Int = 0,
        /// True while a "Mark watched"/"Mark unwatched" call is in
        /// flight — disables the button so a slow link can't queue a
        /// second toggle behind the first.
        val watchedActionInFlight: Boolean = false,
    ) : DetailState
}

class DetailViewModel(
    application: Application,
    private val repo: CatalogRepository,
    private val itemId: String,
) : AndroidViewModel(application) {
    private val _state = MutableStateFlow<DetailState>(DetailState.Loading)
    val state: StateFlow<DetailState> = _state

    private val _transientError = MutableStateFlow<String?>(null)
    val transientError: StateFlow<String?> = _transientError

    fun clearTransientError() {
        _transientError.value = null
    }

    init {
        load()
    }

    fun load() {
        _state.value = DetailState.Loading
        viewModelScope.launch {
            try {
                // QUERY (not GET): one request negotiates the item's
                // source and returns it plus per-source stream info
                // (audio tracks, duration — GET no longer carries these,
                // kahawai commit 5147059) and the subtitle track list
                // with delivery computed for this profile (replaces the
                // deleted `GET /items/{id}/subtitles` call).
                val profile = CapabilityProfileBuilder.build(getApplication())
                val detail = repo.queryItem(itemId, profile)
                val children = if (detail.kind == "show" || detail.kind == "album") {
                    repo.children(itemId)
                } else {
                    emptyList()
                }
                val subtitleTracks = detail.negotiated?.subtitles ?: emptyList()
                _state.value = DetailState.Loaded(detail, children, subtitleTracks)
            } catch (e: Exception) {
                _state.value = DetailState.Error(e.readableMessage(), e.isAuthError())
            }
        }
    }

    /// Re-fetch for a screen that's already showing data — returning from
    /// the player (resume position moved), or the app coming back to the
    /// foreground. Unlike load() this never drops to Loading: the stale
    /// content stays up and is swapped in place, so there's no spinner
    /// flash and no D-pad focus loss. The Loaded guard doubles as
    /// first-composition protection — ON_RESUME also fires right after
    /// init{}'s load() starts, while state is still Loading, and this
    /// silently skips instead of racing it. Track selections survive via
    /// copy(); a failed refresh keeps showing what we have.
    fun refresh() {
        if (_state.value !is DetailState.Loaded) return
        viewModelScope.launch {
            try {
                val profile = CapabilityProfileBuilder.build(getApplication())
                val detail = repo.queryItem(itemId, profile)
                val children = if (detail.kind == "show" || detail.kind == "album") {
                    repo.children(itemId)
                } else {
                    emptyList()
                }
                val current = _state.value as? DetailState.Loaded ?: return@launch
                _state.value = current.copy(
                    detail = detail,
                    children = children,
                    subtitleTracks = detail.negotiated?.subtitles ?: emptyList(),
                )
            } catch (e: Exception) {
                // Best-effort; the screen already has content to show.
            }
        }
    }

    /// Ticks this item watched/unwatched with no playback session
    /// (mirrors web's `useWatched().mark`, item.ts). Marking either
    /// direction clears the resume position server-side, so the local
    /// copy drops it too rather than waiting for a re-fetch.
    fun toggleWatched() {
        val current = _state.value as? DetailState.Loaded ?: return
        if (current.watchedActionInFlight) return
        val target = !current.detail.played
        _state.value = current.copy(watchedActionInFlight = true)
        viewModelScope.launch {
            try {
                val update = repo.setWatched(itemId, target)
                val loaded = _state.value as? DetailState.Loaded ?: return@launch
                _state.value = loaded.copy(
                    detail = loaded.detail.copy(
                        played = update.played,
                        playCount = update.playCount,
                        resumePositionMs = update.positionMs,
                    ),
                    watchedActionInFlight = false,
                )
            } catch (e: Exception) {
                val loaded = _state.value as? DetailState.Loaded ?: return@launch
                _state.value = loaded.copy(watchedActionInFlight = false)
                _transientError.value = e.readableMessage()
            }
        }
    }

    fun selectSubtitleTrack(track: SubtitleTrack?) {
        val current = _state.value as? DetailState.Loaded ?: return
        _state.value = current.copy(selectedSubtitleTrack = track)
    }

    fun selectAudioTrackIndex(index: Int) {
        val current = _state.value as? DetailState.Loaded ?: return
        _state.value = current.copy(selectedAudioTrackIndex = index)
    }
}
