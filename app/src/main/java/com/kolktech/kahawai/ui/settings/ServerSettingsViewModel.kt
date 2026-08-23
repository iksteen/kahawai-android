package com.kolktech.kahawai.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.kolktech.kahawai.data.network.ApiClient
import com.kolktech.kahawai.data.network.dto.PutPrefRequest
import com.kolktech.kahawai.data.network.dto.SetOpenSubtitlesAccountRequest
import com.kolktech.kahawai.data.network.isAuthError
import com.kolktech.kahawai.data.network.readableMessage

sealed interface ServerSettingsState {
    data object Loading : ServerSettingsState
    data class Error(val message: String, val isAuthError: Boolean) : ServerSettingsState
    data class Loaded(
        val values: Map<String, String>,
        /// `GET /api/v1/account/opensubtitles`'s whole answer — the hub's
        /// credential store never reads the account back out, so this is
        /// all a client ever knows about it. See [saveOpenSubtitles].
        val openSubtitlesConfigured: Boolean,
    ) : ServerSettingsState
}

/// Per-user preferences (HUB-33, `GET/PUT /api/v1/prefs`), mirroring
/// `~/code/kahawai/web/src/views/Settings.tsx`'s `values` state — only
/// global-scope (`scope == ""`) keys, which is everything this screen
/// edits. The OpenSubtitles account is its own thing (see
/// [saveOpenSubtitles]): it stopped being a pair of prefs when the hub
/// sealed it into its credential store (commit 7835630).
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
                val configured = ApiClient.apiService().openSubtitlesAccount().configured
                _state.value = ServerSettingsState.Loaded(values, configured)
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
                val current = _state.value as? ServerSettingsState.Loaded ?: return@launch
                val next = if (value.isEmpty()) current.values - key else current.values + (key to value)
                _state.value = current.copy(values = next)
                onSaved()
            } catch (e: Exception) {
                _state.value = ServerSettingsState.Error(e.readableMessage(), e.isAuthError())
            }
        }
    }

    /// Attaches (or replaces) the account via `POST /api/v1/account/opensubtitles`.
    /// The hub stores it whole and never hands it back, so on success there
    /// is nothing to reflect but `configured = true` — [onSaved] is what
    /// clears the form's own username/password fields.
    fun saveOpenSubtitles(username: String, password: String, onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                ApiClient.apiService()
                    .setOpenSubtitlesAccount(SetOpenSubtitlesAccountRequest(username, password))
                val current = _state.value as? ServerSettingsState.Loaded ?: return@launch
                _state.value = current.copy(openSubtitlesConfigured = true)
                onSaved()
            } catch (e: Exception) {
                _state.value = ServerSettingsState.Error(e.readableMessage(), e.isAuthError())
            }
        }
    }

    /// `DELETE /api/v1/account/opensubtitles`. Subtitle searches then fall
    /// back to the deployment's shared anonymous budget.
    fun disconnectOpenSubtitles(onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                ApiClient.apiService().deleteOpenSubtitlesAccount()
                val current = _state.value as? ServerSettingsState.Loaded ?: return@launch
                _state.value = current.copy(openSubtitlesConfigured = false)
                onSaved()
            } catch (e: Exception) {
                _state.value = ServerSettingsState.Error(e.readableMessage(), e.isAuthError())
            }
        }
    }
}
