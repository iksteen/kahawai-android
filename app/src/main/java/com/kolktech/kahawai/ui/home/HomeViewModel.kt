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
import com.kolktech.kahawai.data.network.readableMessage
import com.kolktech.kahawai.data.repository.CatalogRepository

data class LibraryRow(val library: LibrarySummary, val items: List<Item>)

sealed interface HomeState {
    data object Loading : HomeState
    data class Error(val message: String) : HomeState
    data class Loaded(val rows: List<LibraryRow>) : HomeState
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
                val libraries = repo.libraries()
                // One request per library, concurrently — the items
                // endpoint has no "top N per library" shape, mirroring
                // web/src/views/Libraries.tsx:31-34.
                val rows = coroutineScope {
                    libraries
                        .map { library ->
                            async {
                                LibraryRow(
                                    library,
                                    repo.items(library = library.id, sort = "-added", limit = ROW_SIZE).items,
                                )
                            }
                        }
                        .awaitAll()
                }
                _state.value = HomeState.Loaded(rows.filter { it.items.isNotEmpty() })
            } catch (e: Exception) {
                _state.value = HomeState.Error(e.readableMessage())
            }
        }
    }

    private companion object {
        const val ROW_SIZE = 20
    }
}
