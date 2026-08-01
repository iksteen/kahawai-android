package com.kolktech.kahawai.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.kolktech.kahawai.data.network.dto.Item
import com.kolktech.kahawai.data.network.dto.ItemDetail
import com.kolktech.kahawai.data.network.readableMessage
import com.kolktech.kahawai.data.repository.CatalogRepository

sealed interface DetailState {
    data object Loading : DetailState
    data class Error(val message: String) : DetailState
    data class Loaded(val detail: ItemDetail, val children: List<Item>) : DetailState
}

class DetailViewModel(
    private val repo: CatalogRepository,
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
                _state.value = DetailState.Loaded(detail, children)
            } catch (e: Exception) {
                _state.value = DetailState.Error(e.readableMessage())
            }
        }
    }
}
