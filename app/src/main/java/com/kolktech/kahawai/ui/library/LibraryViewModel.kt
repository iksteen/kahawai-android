package com.kolktech.kahawai.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.kolktech.kahawai.data.network.dto.Item
import com.kolktech.kahawai.data.network.isAuthError
import com.kolktech.kahawai.data.network.readableMessage
import com.kolktech.kahawai.data.repository.CatalogRepository

sealed interface LibraryState {
    data object Loading : LibraryState
    data class Error(val message: String, val isAuthError: Boolean = false) : LibraryState
    data class Loaded(
        val items: List<Item>,
        val total: Int,
        val loadingMore: Boolean = false,
    ) : LibraryState
}

/// Backs the "See all" screen off a Home row: the same items() endpoint,
/// paged with limit/offset instead of capped at HomeViewModel.ROW_SIZE.
class LibraryViewModel(
    private val repo: CatalogRepository,
    private val libraryId: String,
) : ViewModel() {
    private val _state = MutableStateFlow<LibraryState>(LibraryState.Loading)
    val state: StateFlow<LibraryState> = _state

    private var loadingMore = false

    init {
        load()
    }

    fun load() {
        _state.value = LibraryState.Loading
        viewModelScope.launch {
            try {
                val response = repo.items(library = libraryId, sort = "title", limit = PAGE_SIZE, offset = 0)
                _state.value = LibraryState.Loaded(response.items, response.total)
            } catch (e: Exception) {
                _state.value = LibraryState.Error(e.readableMessage(), e.isAuthError())
            }
        }
    }

    /// In-place re-fetch of every page currently shown (one request,
    /// offset 0, limit = what's on screen) so watch-progress bars update
    /// when coming back from the player — no Loading flash, no scroll or
    /// focus loss. Skipped while loadMore() is in flight rather than
    /// racing it over who writes the item list last; the next resume
    /// catches up. The Loaded guard also skips the ON_RESUME that fires
    /// during init{}'s own load.
    fun refresh() {
        val current = _state.value as? LibraryState.Loaded ?: return
        if (loadingMore) return
        viewModelScope.launch {
            try {
                val response = repo.items(
                    library = libraryId,
                    sort = "title",
                    limit = maxOf(current.items.size, PAGE_SIZE),
                    offset = 0,
                )
                if (loadingMore) return@launch
                _state.value = LibraryState.Loaded(response.items, response.total)
            } catch (e: Exception) {
                // Keep showing what we have.
            }
        }
    }

    fun loadMore() {
        val current = _state.value as? LibraryState.Loaded ?: return
        if (loadingMore || current.items.size >= current.total) return
        loadingMore = true
        _state.value = current.copy(loadingMore = true)
        viewModelScope.launch {
            try {
                val response = repo.items(
                    library = libraryId,
                    sort = "title",
                    limit = PAGE_SIZE,
                    offset = current.items.size,
                )
                _state.value = LibraryState.Loaded(current.items + response.items, response.total)
            } catch (e: Exception) {
                // Keep what's already loaded; the grid just stops growing.
                _state.value = current.copy(loadingMore = false)
            } finally {
                loadingMore = false
            }
        }
    }

    private companion object {
        const val PAGE_SIZE = 40
    }
}
