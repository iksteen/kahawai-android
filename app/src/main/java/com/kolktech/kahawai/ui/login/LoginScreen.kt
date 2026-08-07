package com.kolktech.kahawai.ui.login

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kolktech.kahawai.R
import com.kolktech.kahawai.data.auth.TokenStore

@Composable
fun LoginScreen(
    tokenStore: TokenStore,
    onLoggedIn: () -> Unit,
) {
    val application = LocalContext.current.applicationContext as Application
    val viewModel: LoginViewModel = viewModel(
        factory = viewModelFactory { initializer { LoginViewModel(application, tokenStore) } },
    )
    val state by viewModel.state.collectAsState()
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    // Collapsed by default regardless of hub state — first-time setup is
    // an escape hatch off the normal login form, not a separate screen.
    var showSetupKey by remember { mutableStateOf(false) }
    var setupToken by remember { mutableStateOf("") }

    LaunchedEffect(state) {
        if (state is LoginState.LoggedIn) onLoggedIn()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.login_sign_in), style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text(stringResource(R.string.username)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .semantics { contentType = ContentType.Username },
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.password)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            // KeyboardType.Password keeps the IME from learning/suggesting
            // the value; the autofill ContentType lets password managers
            // offer (and offer to save) credentials for this hub.
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .semantics { contentType = ContentType.Password },
        )
        TextButton(
            onClick = { showSetupKey = !showSetupKey },
            modifier = Modifier.padding(top = 4.dp),
        ) {
            Text(stringResource(if (showSetupKey) R.string.login_cancel_setup else R.string.login_setup_prompt))
        }
        if (showSetupKey) {
            OutlinedTextField(
                value = setupToken,
                onValueChange = { setupToken = it },
                label = { Text(stringResource(R.string.login_setup_token)) },
                placeholder = { Text(stringResource(R.string.login_setup_token_placeholder)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (state is LoginState.Error) {
            Text(
                (state as LoginState.Error).message,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Button(
            onClick = {
                if (showSetupKey) {
                    viewModel.setup(setupToken, username, password)
                } else {
                    viewModel.login(username, password)
                }
            },
            enabled = state !is LoginState.Loading,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
        ) {
            if (state is LoginState.Loading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp))
            } else {
                Text(stringResource(if (showSetupKey) R.string.login_create_admin else R.string.login_sign_in))
            }
        }
    }
}
