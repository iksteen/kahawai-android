package com.kolktech.kahawai.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import com.kolktech.kahawai.data.auth.ServerConfigStore
import com.kolktech.kahawai.data.auth.TokenStore
import com.kolktech.kahawai.data.network.ApiClient
import com.kolktech.kahawai.data.network.readableMessage

sealed interface ServerSetupState {
    data object Idle : ServerSetupState
    data object Loading : ServerSetupState
    data class Error(val message: String) : ServerSetupState
    data object ReadyForLogin : ServerSetupState
}

class ServerSetupViewModel(
    private val serverConfigStore: ServerConfigStore,
    private val tokenStore: TokenStore,
) : ViewModel() {
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
        // HttpUrl only parses http/https, so this rejects both stray
        // schemes (ftp://…) and typos ("htp://…", "http:/…") outright
        // instead of persisting them and failing on the network.
        val candidate = if (trimmed.contains("://")) trimmed else "http://$trimmed"
        val parsed = candidate.toHttpUrlOrNull()
        if (parsed == null) {
            _state.value = ServerSetupState.Error("That doesn't look like a valid http:// or https:// address")
            return
        }
        checkServer(parsed.toString())
    }

    private fun checkServer(url: String) {
        _state.value = ServerSetupState.Loading
        viewModelScope.launch {
            try {
                // Probed against the candidate URL directly — nothing is
                // persisted until the hub actually answers, so a typo'd
                // address never becomes the stored base URL.
                // Setup completion is no longer checked here — a hub that
                // still needs its first admin account is handled by the
                // Login screen's own setup-token entry, not a separate
                // gate on this address-probing step.
                ApiClient.probeApiService(url).bootstrap()
                val previous = serverConfigStore.baseUrl
                serverConfigStore.baseUrl = url
                ApiClient.reset()
                // Tokens are per-hub; carrying the old hub's pair to a new
                // one just guarantees a 401 (and sends credentials where
                // they don't belong).
                if (previous != null && previous.trimEnd('/') != url.trimEnd('/')) {
                    tokenStore.clear()
                }
                _state.value = ServerSetupState.ReadyForLogin
            } catch (e: Exception) {
                _state.value = ServerSetupState.Error("Couldn't reach $url: ${e.readableMessage()}")
            }
        }
    }
}
