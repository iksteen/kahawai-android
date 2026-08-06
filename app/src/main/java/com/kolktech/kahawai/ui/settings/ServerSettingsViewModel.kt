package com.kolktech.kahawai.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.kolktech.kahawai.data.network.ApiClient
import com.kolktech.kahawai.data.network.dto.PutPrefRequest
import com.kolktech.kahawai.data.network.isAuthError
import com.kolktech.kahawai.data.network.readableMessage

sealed interface ServerSettingsState {
    data object Loading : ServerSettingsState
    data class Error(val message: String, val isAuthError: Boolean) : ServerSettingsState
    data class Loaded(val values: Map<String, String>) : ServerSettingsState
}

/// Per-user preferences (HUB-33, `GET/PUT /api/v1/prefs`), mirroring
/// `~/code/kahawai/web/src/views/Settings.tsx`'s `values` state — only
/// global-scope (`scope == ""`) keys, which is everything this screen
/// edits.
class ServerSettingsViewModel : ViewModel() {
    private val _state = MutableStateFlow<ServerSettingsState>(ServerSettingsState.Loading)
    val state: StateFlow<ServerSettingsState> = _state

    init {
        load()
    }

    fun load() {
        _state.value = ServerSettingsState.Loading
        viewModelScope.launch {
            try {
                val prefs = ApiClient.apiService().prefs().prefs
                val values = prefs.filter { it.scope.isEmpty() }.associate { it.key to it.value }
                _state.value = ServerSettingsState.Loaded(values)
            } catch (e: Exception) {
                _state.value = ServerSettingsState.Error(e.readableMessage(), e.isAuthError())
            }
        }
    }

    /// Fire-and-forget single-key save used by every field on this screen:
    /// optimistically updates the in-memory map so the UI reflects the new
    /// value immediately, same as the web client's per-field `commit`.
    fun setPref(key: String, value: String, onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                ApiClient.apiService().putPref(PutPrefRequest(key = key, value = value))
                val current = (_state.value as? ServerSettingsState.Loaded)?.values ?: emptyMap()
                val next = if (value.isEmpty()) current - key else current + (key to value)
                _state.value = ServerSettingsState.Loaded(next)
                onSaved()
            } catch (e: Exception) {
                _state.value = ServerSettingsState.Error(e.readableMessage(), e.isAuthError())
            }
        }
    }

    /// Both opensubtitles.* keys save (or, for Disconnect, clear) together —
    /// mirrors `OpenSubtitlesAccount.save` in Settings.tsx:132-144.
    fun saveOpenSubtitles(username: String, password: String, onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                ApiClient.apiService().putPref(PutPrefRequest(key = "opensubtitles.username", value = username))
                ApiClient.apiService().putPref(PutPrefRequest(key = "opensubtitles.password", value = password))
                val current = (_state.value as? ServerSettingsState.Loaded)?.values ?: emptyMap()
                val next = current.toMutableMap()
                if (username.isEmpty()) next.remove("opensubtitles.username") else next["opensubtitles.username"] = username
                if (password.isEmpty()) next.remove("opensubtitles.password") else next["opensubtitles.password"] = password
                _state.value = ServerSettingsState.Loaded(next)
                onSaved()
            } catch (e: Exception) {
                _state.value = ServerSettingsState.Error(e.readableMessage(), e.isAuthError())
            }
        }
    }
}
