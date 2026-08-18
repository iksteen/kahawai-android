package com.kolktech.kahawai.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kolktech.kahawai.R
import com.kolktech.kahawai.data.settings.AppSettingsStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSettingsScreen(
    appSettingsStore: AppSettingsStore,
    onBack: () -> Unit,
    onOpenSeeking: () -> Unit,
) {
    var gesturesEnabled by remember { mutableStateOf(appSettingsStore.playerGesturesEnabled) }
    var volumeBrightnessEnabled by remember { mutableStateOf(appSettingsStore.volumeBrightnessGestureEnabled) }
    var seekGestureEnabled by remember { mutableStateOf(appSettingsStore.seekGestureEnabled) }
    var zoomGestureEnabled by remember { mutableStateOf(appSettingsStore.zoomGestureEnabled) }
    var rememberBrightness by remember { mutableStateOf(appSettingsStore.rememberBrightnessLevel) }
    val seekBackMs = remember { mutableStateOf(appSettingsStore.seekBackMs) }
    val seekForwardMs = remember { mutableStateOf(appSettingsStore.seekForwardMs) }
    var autoSkipIntrosOutros by remember { mutableStateOf(appSettingsStore.autoSkipIntrosOutros) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.player_settings_title)) },
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SettingsSectionTitle(stringResource(R.string.player_settings_gestures))
            SettingsSwitchRow(
                title = stringResource(R.string.player_settings_gestures_enabled),
                description = stringResource(R.string.player_settings_gestures_enabled_hint),
                checked = gesturesEnabled,
                onCheckedChange = {
                    gesturesEnabled = it
                    appSettingsStore.playerGesturesEnabled = it
                },
            )
            SettingsSwitchRow(
                title = stringResource(R.string.player_settings_volume_brightness),
                description = stringResource(R.string.player_settings_volume_brightness_hint),
                checked = volumeBrightnessEnabled,
                enabled = gesturesEnabled,
                onCheckedChange = {
                    volumeBrightnessEnabled = it
                    appSettingsStore.volumeBrightnessGestureEnabled = it
                },
            )
            SettingsSwitchRow(
                title = stringResource(R.string.player_settings_seek_gesture),
                description = stringResource(R.string.player_settings_seek_gesture_hint),
                checked = seekGestureEnabled,
                enabled = gesturesEnabled,
                onCheckedChange = {
                    seekGestureEnabled = it
                    appSettingsStore.seekGestureEnabled = it
                },
            )
            SettingsSwitchRow(
                title = stringResource(R.string.player_settings_zoom_gesture),
                description = stringResource(R.string.player_settings_zoom_gesture_hint),
                checked = zoomGestureEnabled,
                enabled = gesturesEnabled,
                onCheckedChange = {
                    zoomGestureEnabled = it
                    appSettingsStore.zoomGestureEnabled = it
                },
            )
            SettingsSwitchRow(
                title = stringResource(R.string.player_settings_remember_brightness),
                description = stringResource(R.string.player_settings_remember_brightness_hint),
                checked = rememberBrightness,
                enabled = gesturesEnabled,
                onCheckedChange = {
                    rememberBrightness = it
                    appSettingsStore.rememberBrightnessLevel = it
                },
            )

            SettingsSectionTitle(stringResource(R.string.player_settings_seeking))
            SettingsNavigationRow(
                title = stringResource(R.string.player_settings_seeking),
                description = stringResource(
                    R.string.player_settings_seeking_desc,
                    seekBackMs.value / 1000,
                    seekForwardMs.value / 1000,
                ),
                onClick = onOpenSeeking,
            )

            SettingsSectionTitle(stringResource(R.string.player_settings_skipping))
            SettingsSwitchRow(
                title = stringResource(R.string.player_settings_auto_skip),
                description = stringResource(R.string.player_settings_auto_skip_hint),
                checked = autoSkipIntrosOutros,
                onCheckedChange = {
                    autoSkipIntrosOutros = it
                    appSettingsStore.autoSkipIntrosOutros = it
                },
            )
        }
    }
}
