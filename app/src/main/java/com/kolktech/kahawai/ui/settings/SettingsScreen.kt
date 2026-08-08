package com.kolktech.kahawai.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kolktech.kahawai.R

/// Landing page for every device-local and per-user-on-hub setting —
/// replaces the old flat "App settings" screen. Each row below navigates
/// into its own category screen.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    isAdmin: Boolean,
    onBack: () -> Unit,
    onOpenLanguage: () -> Unit,
    onOpenInterface: () -> Unit,
    onOpenPlayer: () -> Unit,
    onOpenNetwork: () -> Unit,
    onOpenServerSettings: () -> Unit,
    onOpenAdmin: () -> Unit,
    onOpenCache: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.kw_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsCategoryRow(
                icon = Icons.Default.Language,
                title = stringResource(R.string.settings_language),
                description = stringResource(R.string.settings_language_desc),
                onClick = onOpenLanguage,
            )
            SettingsCategoryRow(
                icon = Icons.Default.Fullscreen,
                title = stringResource(R.string.settings_interface),
                description = stringResource(R.string.settings_interface_desc),
                onClick = onOpenInterface,
            )
            SettingsCategoryRow(
                icon = Icons.Default.PlayCircle,
                title = stringResource(R.string.settings_player),
                description = stringResource(R.string.settings_player_desc),
                onClick = onOpenPlayer,
            )
            SettingsCategoryRow(
                icon = Icons.Default.Wifi,
                title = stringResource(R.string.settings_network),
                description = stringResource(R.string.settings_network_desc),
                onClick = onOpenNetwork,
            )
            SettingsCategoryRow(
                icon = Icons.Default.CloudSync,
                title = stringResource(R.string.settings_server_settings),
                description = stringResource(R.string.settings_server_settings_desc),
                onClick = onOpenServerSettings,
            )
            if (isAdmin) {
                SettingsCategoryRow(
                    icon = Icons.Default.AdminPanelSettings,
                    title = stringResource(R.string.settings_server_admin_settings),
                    description = stringResource(R.string.settings_server_admin_settings_desc),
                    onClick = onOpenAdmin,
                )
            }
            SettingsCategoryRow(
                icon = Icons.Default.Storage,
                title = stringResource(R.string.settings_cache),
                description = stringResource(R.string.settings_cache_desc),
                onClick = onOpenCache,
            )
            SettingsCategoryRow(
                icon = Icons.Default.Info,
                title = stringResource(R.string.settings_about),
                description = stringResource(R.string.settings_about_desc),
                onClick = onOpenAbout,
            )
        }
    }
}
