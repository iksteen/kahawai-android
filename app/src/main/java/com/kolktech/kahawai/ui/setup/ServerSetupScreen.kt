package com.kolktech.kahawai.ui.setup

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kolktech.kahawai.data.auth.ServerConfigStore

@Composable
fun ServerSetupScreen(
    serverConfigStore: ServerConfigStore,
    onReady: () -> Unit,
) {
    val viewModel: ServerSetupViewModel = viewModel(
        factory = viewModelFactory { initializer { ServerSetupViewModel(serverConfigStore) } },
    )
    val state by viewModel.state.collectAsState()
    var urlInput by remember { mutableStateOf(serverConfigStore.baseUrl.orEmpty()) }

    LaunchedEffect(state) {
        if (state is ServerSetupState.ReadyForLogin) onReady()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Connect to your kahawai hub", style = MaterialTheme.typography.headlineSmall)
        Text(
            "e.g. 192.168.1.20:8420 or https://kahawai.example.com",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )
        OutlinedTextField(
            value = urlInput,
            onValueChange = { urlInput = it },
            label = { Text("Hub address") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (state is ServerSetupState.Error) {
            Text(
                (state as ServerSetupState.Error).message,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Button(
            onClick = { viewModel.submit(urlInput) },
            enabled = state !is ServerSetupState.Loading,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
        ) {
            if (state is ServerSetupState.Loading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp))
            } else {
                Text("Continue")
            }
        }
    }
}
