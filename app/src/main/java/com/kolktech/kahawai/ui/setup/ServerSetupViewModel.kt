package com.kolktech.kahawai.ui.setup

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import com.kolktech.kahawai.R
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
    application: Application,
    private val serverConfigStore: ServerConfigStore,
    private val tokenStore: TokenStore,
) : AndroidViewModel(application) {
    private val _state = MutableStateFlow<ServerSetupState>(ServerSetupState.Idle)
    val state: StateFlow<ServerSetupState> = _state

    init {
        serverConfigStore.baseUrl?.let { checkServer(it) }
    }

    fun submit(rawUrl: String) {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) {
            _state.value = ServerSetupState.Error(getApplication<Application>().getString(R.string.setup_enter_address))
            return
        }
        // HttpUrl only parses http/https, so this rejects both stray
        // schemes (ftp://…) and typos ("htp://…", "http:/…") outright
        // instead of persisting them and failing on the network.
        val candidate = if (trimmed.contains("://")) trimmed else "http://$trimmed"
        val parsed = candidate.toHttpUrlOrNull()
        if (parsed == null) {
            _state.value = ServerSetupState.Error(getApplication<Application>().getString(R.string.setup_invalid_address))
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
                // The candidate is first resolved through any http→https
                // redirect the host serves (see [ApiClient.resolveBaseUrl]):
                // storing the pre-redirect URL would leave every
                // authenticated request bouncing through a redirect that
                // strips its Authorization header.
                val resolved = ApiClient.resolveBaseUrl(url)
                ApiClient.probeApiService(resolved).bootstrap()
                val previous = serverConfigStore.baseUrl
                serverConfigStore.baseUrl = resolved
                ApiClient.reset()
                // Tokens are per-hub; carrying the old hub's pair to a new
                // one just guarantees a 401 (and sends credentials where
                // they don't belong).
                if (previous != null && previous.trimEnd('/') != resolved.trimEnd('/')) {
                    tokenStore.clear()
                }
                _state.value = ServerSetupState.ReadyForLogin
            } catch (e: Exception) {
                _state.value = ServerSetupState.Error(getApplication<Application>().getString(R.string.setup_unreachable, url, e.readableMessage()))
            }
        }
    }
}
