package com.kolktech.kahawai.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.kolktech.kahawai.data.auth.TokenStore
import com.kolktech.kahawai.data.network.ApiClient
import com.kolktech.kahawai.data.network.dto.LoginRequest
import com.kolktech.kahawai.data.network.dto.SetupRequest
import com.kolktech.kahawai.data.network.readableMessage

sealed interface LoginState {
    data object Idle : LoginState
    data object Loading : LoginState
    data class Error(val message: String) : LoginState
    data object LoggedIn : LoginState
}

class LoginViewModel(private val tokenStore: TokenStore) : ViewModel() {
    private val _state = MutableStateFlow<LoginState>(LoginState.Idle)
    val state: StateFlow<LoginState> = _state

    fun login(username: String, password: String) {
        if (username.isBlank() || password.isEmpty()) {
            _state.value = LoginState.Error("Enter a username and password")
            return
        }
        _state.value = LoginState.Loading
        viewModelScope.launch {
            try {
                val tokens = ApiClient.plainApiService().login(LoginRequest(username, password))
                tokenStore.save(tokens)
                _state.value = LoginState.LoggedIn
            } catch (e: Exception) {
                _state.value = LoginState.Error("Login failed: ${e.readableMessage()}")
            }
        }
    }

    /// First-time hub setup: creates the admin account from the hub's
    /// console-printed setup token instead of signing in to an existing
    /// one (see [com.kolktech.kahawai.data.network.ApiService.setup]).
    fun setup(token: String, username: String, password: String) {
        if (token.isBlank() || username.isBlank() || password.isEmpty()) {
            _state.value = LoginState.Error("Enter the setup token, a username, and a password")
            return
        }
        _state.value = LoginState.Loading
        viewModelScope.launch {
            try {
                val tokens = ApiClient.plainApiService().setup(SetupRequest(token, username, password))
                tokenStore.save(tokens)
                _state.value = LoginState.LoggedIn
            } catch (e: Exception) {
                _state.value = LoginState.Error("Setup failed: ${e.readableMessage()}")
            }
        }
    }
}
