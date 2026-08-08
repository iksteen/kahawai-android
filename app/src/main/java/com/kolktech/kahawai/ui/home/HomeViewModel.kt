package com.kolktech.kahawai.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.kolktech.kahawai.data.network.dto.Item
import com.kolktech.kahawai.data.network.dto.LibrarySummary
import com.kolktech.kahawai.data.network.isAuthError
import com.kolktech.kahawai.data.network.readableMessage
import com.kolktech.kahawai.data.repository.CatalogRepository

data class LibraryRow(val library: LibrarySummary, val items: List<Item>, val total: Int)

sealed interface HomeState {
    data object Loading : HomeState
    data class Error(val message: String, val isAuthError: Boolean = false) : HomeState
    data class Loaded(val rows: List<LibraryRow>, val isRefreshing: Boolean = false) : HomeState
}

class HomeViewModel(private val repo: CatalogRepository) : ViewModel() {
    private val _state = MutableStateFlow<HomeState>(HomeState.Loading)
    val state: StateFlow<HomeState> = _state

    init {
        load()
    }

    fun load() {
        _state.value = HomeState.Loading
        viewModelScope.launch {
            try {
                _state.value = HomeState.Loaded(fetchRows())
            } catch (e: Exception) {
                _state.value = HomeState.Error(e.readableMessage(), e.isAuthError())
            }
        }
    }

    /// In-place re-fetch for an already-showing screen (back from the
    /// player, app foregrounded, pull-to-refresh, TV reload button) —
    /// keeps the current rows up instead of dropping to the Loading
    /// spinner, so watch-progress bars update without a flash or focus
    /// loss. The Loaded guard also skips the ON_RESUME that fires during
    /// init{}'s own load. isRefreshing drives the pull-to-refresh
    /// indicator; best-effort fetch keeps what's shown on failure.
    fun refresh() {
        val current = _state.value as? HomeState.Loaded ?: return
        _state.value = current.copy(isRefreshing = true)
        viewModelScope.launch {
            _state.value = try {
                HomeState.Loaded(fetchRows())
            } catch (e: Exception) {
                current.copy(isRefreshing = false)
            }
        }
    }

    private suspend fun fetchRows(): List<LibraryRow> {
        val libraries = repo.libraries()
        // One request per library, concurrently — the items
        // endpoint has no "top N per library" shape, mirroring
        // web/src/views/Libraries.tsx:31-34.
        val rows = coroutineScope {
            libraries
                .map { library ->
                    async {
                        val response = repo.items(library = library.id, sort = "-added", limit = ROW_SIZE)
                        LibraryRow(library, response.items, response.total)
                    }
                }
                .awaitAll()
        }
        return rows.filter { it.items.isNotEmpty() }
    }

    private companion object {
        const val ROW_SIZE = 20
    }
}
