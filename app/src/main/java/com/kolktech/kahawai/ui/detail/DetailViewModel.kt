package com.kolktech.kahawai.ui.detail

import androidx.lifecycle.ViewModel
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
import com.kolktech.kahawai.data.repository.PlaybackRepository

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
    ) : DetailState
}

class DetailViewModel(
    private val repo: CatalogRepository,
    private val playbackRepo: PlaybackRepository,
    private val itemId: String,
) : ViewModel() {
    private val _state = MutableStateFlow<DetailState>(DetailState.Loading)
    val state: StateFlow<DetailState> = _state

    init {
        load()
    }

    fun load() {
        _state.value = DetailState.Loading
        viewModelScope.launch {
            try {
                val detail = repo.item(itemId)
                val children = if (detail.kind == "show" || detail.kind == "album") {
                    repo.children(itemId)
                } else {
                    emptyList()
                }
                // Lets the viewer pick a subtitle/audio track before
                // starting playback — subtitles come straight from the
                // hub's own track list (no session needed); audio tracks
                // come from the first source's own probe, already part
                // of the detail response.
                val subtitleTracks = if (detail.kind !in NOT_DIRECTLY_PLAYABLE) {
                    try {
                        playbackRepo.subtitles(itemId)
                    } catch (e: Exception) {
                        emptyList()
                    }
                } else {
                    emptyList()
                }
                _state.value = DetailState.Loaded(detail, children, subtitleTracks)
            } catch (e: Exception) {
                _state.value = DetailState.Error(e.readableMessage(), e.isAuthError())
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
