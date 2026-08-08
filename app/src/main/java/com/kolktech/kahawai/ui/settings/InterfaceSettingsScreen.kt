package com.kolktech.kahawai.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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

/// [reserveNotchSpace]/[onReserveNotchSpaceChange] are threaded in from
/// [com.kolktech.kahawai.ui.nav.KahawaiNavGraph] rather than read straight
/// from the store here, so a toggle takes effect on this very screen the
/// instant it's flipped — the nav graph's own window-inset handling reacts
/// to the same Compose state, not just to a later route change.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InterfaceSettingsScreen(
    reserveNotchSpace: Boolean,
    onReserveNotchSpaceChange: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.interface_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.kw_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_reserve_notch_space),
                description = stringResource(R.string.settings_reserve_notch_space_hint),
                checked = reserveNotchSpace,
                onCheckedChange = onReserveNotchSpaceChange,
            )
        }
    }
}
