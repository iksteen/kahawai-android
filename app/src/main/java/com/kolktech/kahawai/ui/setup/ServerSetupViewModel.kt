package com.kolktech.kahawai.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.kolktech.kahawai.data.auth.ServerConfigStore
import com.kolktech.kahawai.data.network.ApiClient
import com.kolktech.kahawai.data.network.readableMessage

sealed interface ServerSetupState {
    data object Idle : ServerSetupState
    data object Loading : ServerSetupState
    data class Error(val message: String) : ServerSetupState
    data object ReadyForLogin : ServerSetupState
}

class ServerSetupViewModel(private val serverConfigStore: ServerConfigStore) : ViewModel() {
    private val _state = MutableStateFlow<ServerSetupState>(ServerSetupState.Idle)
    val state: StateFlow<ServerSetupState> = _state

    init {
        serverConfigStore.baseUrl?.let { checkServer(it) }
    }

    fun submit(rawUrl: String) {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) {
            _state.value = ServerSetupState.Error("Enter your hub's address")
            return
        }
        val url = if (trimmed.contains("://")) trimmed else "http://$trimmed"
        checkServer(url)
    }

    private fun checkServer(url: String) {
        _state.value = ServerSetupState.Loading
        serverConfigStore.baseUrl = url
        ApiClient.reset()
        viewModelScope.launch {
            try {
                val bootstrap = ApiClient.plainApiService().bootstrap()
                _state.value = if (bootstrap.setupRequired) {
                    ServerSetupState.Error(
                        "This hub hasn't completed first-time setup yet. " +
                            "Finish setup via the web UI, then come back here.",
                    )
                } else {
                    ServerSetupState.ReadyForLogin
                }
            } catch (e: Exception) {
                _state.value = ServerSetupState.Error("Couldn't reach $url: ${e.readableMessage()}")
            }
        }
    }
}
