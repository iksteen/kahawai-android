package com.kolktech.kahawai.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import com.kolktech.kahawai.data.network.dto.Item
import com.kolktech.kahawai.data.network.readableMessage
import com.kolktech.kahawai.data.repository.CatalogRepository

sealed interface SearchState {
    data object Idle : SearchState
    data object Loading : SearchState
    data class Error(val message: String) : SearchState
    data class Loaded(val items: List<Item>, val total: Int) : SearchState
}

private const val DEBOUNCE_MS = 300L
private const val RESULT_LIMIT = 60

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class SearchViewModel(private val repo: CatalogRepository) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _state = MutableStateFlow<SearchState>(SearchState.Idle)
    val state: StateFlow<SearchState> = _state

    init {
        viewModelScope.launch {
            _query
                .debounce(DEBOUNCE_MS)
                .distinctUntilChanged()
                .collectLatest { q ->
                    if (q.isBlank()) {
                        _state.value = SearchState.Idle
                        return@collectLatest
                    }
                    _state.value = SearchState.Loading
                    try {
                        val result = repo.items(q = q, limit = RESULT_LIMIT)
                        _state.value = SearchState.Loaded(result.items, result.total)
                    } catch (e: Exception) {
                        _state.value = SearchState.Error(e.readableMessage())
                    }
                }
        }
    }

    fun onQueryChange(q: String) {
        _query.value = q
    }
}
