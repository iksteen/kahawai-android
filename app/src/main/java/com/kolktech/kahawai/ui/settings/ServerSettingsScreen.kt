package com.kolktech.kahawai.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kolktech.kahawai.R
import com.kolktech.kahawai.ui.components.ErrorView

private val MEDIA_TYPES = listOf("movies", "series", "anime")
private val ASS_RUNGS = listOf("flatten", "overlay", "burn")

/// Per-user hub preferences (HUB-33) — audio/subtitle language defaults,
/// OpenSubtitles account, bandwidth cap, ASS fallback order. Mirrors
/// `~/code/kahawai/web/src/views/Settings.tsx`, with its interactive chip
/// pickers simplified to plain comma-separated text fields (same
/// underlying pref keys/format, so either client can edit the other's
/// values).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSettingsScreen(
    onBack: () -> Unit,
    onSessionExpired: () -> Unit,
) {
    val viewModel: ServerSettingsViewModel = viewModel(
        factory = viewModelFactory { initializer { ServerSettingsViewModel() } },
    )
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.server_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.kw_back))
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                is ServerSettingsState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is ServerSettingsState.Error -> ErrorView(
                    message = s.message,
                    isAuthError = s.isAuthError,
                    onRetry = { viewModel.load() },
                    onSignInAgain = onSessionExpired,
                )
                is ServerSettingsState.Loaded -> LoadedContent(s.values, viewModel)
            }
        }
    }
}

@Composable
private fun LoadedContent(values: Map<String, String>, viewModel: ServerSettingsViewModel) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        item { OpenSubtitlesSection(values, viewModel) }
        item { PlaybackSection(values, viewModel) }
        items(MEDIA_TYPES) { mediaType -> MediaTypeSection(mediaType, values, viewModel) }
        item { Spacer(Modifier.padding(bottom = 24.dp)) }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun OpenSubtitlesSection(values: Map<String, String>, viewModel: ServerSettingsViewModel) {
    var username by remember(values["opensubtitles.username"]) {
        mutableStateOf(values["opensubtitles.username"].orEmpty())
    }
    var password by remember { mutableStateOf("") }
    val hasPassword = values.containsKey("opensubtitles.password")

    SectionTitle(stringResource(R.string.server_settings_opensubtitles))
    Text(
        stringResource(R.string.server_settings_opensubtitles_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedTextField(
        value = username,
        onValueChange = { username = it },
        label = { Text(stringResource(R.string.username)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text(stringResource(if (hasPassword) R.string.settings_password_saved else R.string.password)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
    Row(modifier = Modifier.padding(top = 8.dp)) {
        Button(
            onClick = { viewModel.saveOpenSubtitles(username.trim(), password.trim()) { password = "" } },
            enabled = username.isNotBlank() && password.isNotBlank(),
        ) { Text(stringResource(R.string.save)) }
        if (username.isNotEmpty() || hasPassword) {
            OutlinedButton(
                onClick = { username = ""; viewModel.saveOpenSubtitles("", "") },
                modifier = Modifier.padding(start = 8.dp),
            ) { Text(stringResource(R.string.disconnect)) }
        }
    }
}

@Composable
private fun PlaybackSection(values: Map<String, String>, viewModel: ServerSettingsViewModel) {
    var bandwidth by remember(values["bandwidth_kbps"]) {
        mutableStateOf(values["bandwidth_kbps"].orEmpty())
    }

    SectionTitle(stringResource(R.string.server_settings_playback))
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = bandwidth,
            onValueChange = { bandwidth = it.filter(Char::isDigit) },
            label = { Text(stringResource(R.string.server_settings_bandwidth)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
        Button(
            onClick = { viewModel.setPref("bandwidth_kbps", if (bandwidth == "0") "" else bandwidth) },
            modifier = Modifier.padding(start = 8.dp),
        ) { Text(stringResource(R.string.save)) }
    }

    Text(
        stringResource(R.string.server_settings_ass_fallback_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
    val order = remember(values["ass_order"]) { assOrder(values["ass_order"]) }
    order.forEachIndexed { index, rung ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.numbered_item, index + 1, rung), modifier = Modifier.weight(1f))
            IconButton(
                enabled = index > 0,
                onClick = {
                    val next = order.toMutableList()
                    next[index] = order[index - 1]
                    next[index - 1] = rung
                    viewModel.setPref("ass_order", next.joinToString(","))
                },
            ) { Icon(Icons.Default.KeyboardArrowUp, contentDescription = stringResource(R.string.move_up)) }
            IconButton(
                enabled = index < order.size - 1,
                onClick = {
                    val next = order.toMutableList()
                    next[index] = order[index + 1]
                    next[index + 1] = rung
                    viewModel.setPref("ass_order", next.joinToString(","))
                },
            ) { Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.move_down)) }
        }
    }
}

/// Whatever the stored value leaves out is appended, mirroring the
/// server's own parse (Settings.tsx:206-210) — always a full permutation
/// of [ASS_RUNGS].
private fun assOrder(stored: String?): List<String> {
    val parsed = stored.orEmpty().split(",").map { it.trim() }.filter { it in ASS_RUNGS }
    return (parsed + ASS_RUNGS).distinct()
}

@Composable
private fun MediaTypeSection(mediaType: String, values: Map<String, String>, viewModel: ServerSettingsViewModel) {
    var audio by remember(values["audio.$mediaType"]) {
        mutableStateOf(values["audio.$mediaType"].orEmpty())
    }
    var subs by remember(values["subs.$mediaType"]) {
        mutableStateOf(values["subs.$mediaType"].orEmpty())
    }

    SectionTitle(mediaType)
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = audio,
            onValueChange = { audio = it },
            label = { Text(stringResource(R.string.server_settings_audio_hint)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Button(
            onClick = { viewModel.setPref("audio.$mediaType", audio.trim()) },
            modifier = Modifier.padding(start = 8.dp),
        ) { Text(stringResource(R.string.save)) }
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        OutlinedTextField(
            value = subs,
            onValueChange = { subs = it },
            label = { Text(stringResource(R.string.server_settings_subs_hint)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Button(
            onClick = { viewModel.setPref("subs.$mediaType", subs.trim()) },
            modifier = Modifier.padding(start = 8.dp),
        ) { Text(stringResource(R.string.save)) }
    }
    if (mediaType == "anime") {
        val view = values["anime_view"] ?: "seasons"
        Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("seasons", "native").forEach { option ->
                FilterChip(
                    selected = view == option,
                    onClick = { viewModel.setPref("anime_view", option) },
                    label = { Text(option) },
                )
            }
        }
    }
}
