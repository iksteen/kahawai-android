package com.kolktech.kahawai.ui.login

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.kolktech.kahawai.R
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

class LoginViewModel(application: Application, private val tokenStore: TokenStore) : AndroidViewModel(application) {
    private val _state = MutableStateFlow<LoginState>(LoginState.Idle)
    val state: StateFlow<LoginState> = _state

    fun login(username: String, password: String) {
        if (username.isBlank() || password.isEmpty()) {
            _state.value = LoginState.Error(getApplication<Application>().getString(R.string.login_enter_username_password))
            return
        }
        _state.value = LoginState.Loading
        viewModelScope.launch {
            try {
                val tokens = ApiClient.plainApiService().login(LoginRequest(username, password))
                tokenStore.save(tokens)
                _state.value = LoginState.LoggedIn
            } catch (e: Exception) {
                _state.value = LoginState.Error(getApplication<Application>().getString(R.string.login_failed, e.readableMessage()))
            }
        }
    }

    /// First-time hub setup: creates the admin account from the hub's
    /// console-printed setup token instead of signing in to an existing
    /// one (see [com.kolktech.kahawai.data.network.ApiService.setup]).
    fun setup(token: String, username: String, password: String) {
        if (token.isBlank() || username.isBlank() || password.isEmpty()) {
            _state.value = LoginState.Error(getApplication<Application>().getString(R.string.login_enter_setup_fields))
            return
        }
        _state.value = LoginState.Loading
        viewModelScope.launch {
            try {
                val tokens = ApiClient.plainApiService().setup(SetupRequest(token, username, password))
                tokenStore.save(tokens)
                _state.value = LoginState.LoggedIn
            } catch (e: Exception) {
                _state.value = LoginState.Error(getApplication<Application>().getString(R.string.login_setup_failed, e.readableMessage()))
            }
        }
    }
}
