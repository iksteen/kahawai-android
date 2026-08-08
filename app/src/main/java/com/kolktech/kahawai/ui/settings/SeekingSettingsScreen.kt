package com.kolktech.kahawai.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kolktech.kahawai.R
import com.kolktech.kahawai.data.settings.AppSettingsStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeekingSettingsScreen(
    appSettingsStore: AppSettingsStore,
    onBack: () -> Unit,
) {
    var seekBackText by remember { mutableStateOf(appSettingsStore.seekBackMs.toString()) }
    var seekForwardText by remember { mutableStateOf(appSettingsStore.seekForwardMs.toString()) }

    fun commitSeekBack(text: String) {
        seekBackText = text
        text.toLongOrNull()?.takeIf { it > 0 }?.let { appSettingsStore.seekBackMs = it }
    }

    fun commitSeekForward(text: String) {
        seekForwardText = text
        text.toLongOrNull()?.takeIf { it > 0 }?.let { appSettingsStore.seekForwardMs = it }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.seeking_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.kw_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = seekBackText,
                onValueChange = ::commitSeekBack,
                label = { Text(stringResource(R.string.seeking_settings_back_increment)) },
                suffix = { Text(stringResource(R.string.seeking_settings_ms_suffix)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = seekForwardText,
                onValueChange = ::commitSeekForward,
                label = { Text(stringResource(R.string.seeking_settings_forward_increment)) },
                suffix = { Text(stringResource(R.string.seeking_settings_ms_suffix)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
