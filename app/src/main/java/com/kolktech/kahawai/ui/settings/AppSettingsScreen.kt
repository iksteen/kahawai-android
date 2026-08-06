package com.kolktech.kahawai.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kolktech.kahawai.BuildConfig
import com.kolktech.kahawai.data.auth.ServerConfigStore
import com.kolktech.kahawai.data.auth.TokenStore
import kotlinx.coroutines.launch

/// App-local settings — hub address and app info. Distinct from
/// [ServerSettingsScreen], which edits per-user preferences stored on the
/// hub itself (`/api/v1/prefs`).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen(
    serverConfigStore: ServerConfigStore,
    tokenStore: TokenStore,
    onBack: () -> Unit,
    onChangeServer: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
            Text("Hub server", style = MaterialTheme.typography.titleMedium)
            Text(
                serverConfigStore.baseUrl ?: "not configured",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            OutlinedButton(
                onClick = {
                    coroutineScope.launch {
                        tokenStore.clear()
                        serverConfigStore.baseUrl = null
                        onChangeServer()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Change server")
            }

            Text(
                "About",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 32.dp),
            )
            Text(
                "kahawai ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
