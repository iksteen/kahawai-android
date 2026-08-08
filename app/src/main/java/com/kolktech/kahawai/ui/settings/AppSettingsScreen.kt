package com.kolktech.kahawai.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kolktech.kahawai.BuildConfig
import com.kolktech.kahawai.R
import com.kolktech.kahawai.data.auth.ServerConfigStore
import com.kolktech.kahawai.data.auth.TokenStore
import com.kolktech.kahawai.data.settings.AppSettingsStore
import kotlinx.coroutines.launch

/// App-local settings — hub address and app info. Distinct from
/// [ServerSettingsScreen], which edits per-user preferences stored on the
/// hub itself (`/api/v1/prefs`).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen(
    serverConfigStore: ServerConfigStore,
    tokenStore: TokenStore,
    appSettingsStore: AppSettingsStore,
    onBack: () -> Unit,
    onChangeServer: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    var reserveNotchSpace by remember { mutableStateOf(appSettingsStore.reserveNotchSpace) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.kw_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
            Text(stringResource(R.string.app_settings_hub_server), style = MaterialTheme.typography.titleMedium)
            Text(
                serverConfigStore.baseUrl ?: stringResource(R.string.app_settings_not_configured),
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
                Text(stringResource(R.string.app_settings_change_server))
            }

            Text(
                stringResource(R.string.app_settings_display),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 32.dp),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.app_settings_reserve_notch_space),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        stringResource(R.string.app_settings_reserve_notch_space_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = reserveNotchSpace,
                    onCheckedChange = {
                        reserveNotchSpace = it
                        appSettingsStore.reserveNotchSpace = it
                    },
                )
            }

            Text(
                stringResource(R.string.app_settings_about),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 32.dp),
            )
            Text(
                stringResource(R.string.app_settings_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
