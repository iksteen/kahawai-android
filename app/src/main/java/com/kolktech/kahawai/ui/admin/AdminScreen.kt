package com.kolktech.kahawai.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kolktech.kahawai.data.network.dto.AdminLibrary
import com.kolktech.kahawai.data.network.dto.CollectionInfo
import com.kolktech.kahawai.data.network.dto.PendingEnrollment
import com.kolktech.kahawai.data.network.dto.ProviderChain
import com.kolktech.kahawai.data.network.dto.Satellite
import com.kolktech.kahawai.data.network.dto.AdminSession
import com.kolktech.kahawai.ui.components.ErrorView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/// Fallback refresh interval — same as the web client's own `POLL_MS`
/// (`~/code/kahawai/web/src/views/Admin.tsx:36`); it has an SSE push
/// channel on top of this, which this screen doesn't replicate (see plan).
private const val POLL_MS = 15_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    onBack: () -> Unit,
    onSessionExpired: () -> Unit,
) {
    val viewModel: AdminViewModel = viewModel(
        factory = viewModelFactory { initializer { AdminViewModel() } },
    )
    val state by viewModel.state.collectAsState()
    val notice by viewModel.notice.collectAsState()

    LaunchedEffect(Unit) {
        while (isActive) {
            delay(POLL_MS)
            viewModel.reload()
        }
    }
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(3000)
            viewModel.clearNotice()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Admin") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                is AdminState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is AdminState.Error -> ErrorView(
                    message = s.message,
                    isAuthError = s.isAuthError,
                    onRetry = { viewModel.reload() },
                    onSignInAgain = onSessionExpired,
                )
                is AdminState.Loaded -> Column(Modifier.fillMaxSize()) {
                    notice?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                        )
                    }
                    AdminContent(s.data, viewModel)
                }
            }
        }
    }
}

@Composable
private fun AdminContent(data: AdminData, viewModel: AdminViewModel) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        item { ProvidersSection(data, viewModel) }
        item { EnrollmentsSection(data.pending, viewModel) }
        item { SatellitesSection(data.satellites, viewModel) }
        item { LibrariesSection(data.libraries, data.collections, viewModel) }
        item { SessionsSection(data.sessions, viewModel) }
        item { Box(Modifier.padding(bottom = 32.dp)) }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 28.dp, bottom = 8.dp),
    )
}

// ---- Metadata providers ----

@Composable
private fun ProvidersSection(data: AdminData, viewModel: AdminViewModel) {
    var tmdbKey by remember { mutableStateOf("") }
    var tvdbKey by remember { mutableStateOf("") }
    var tvdbPin by remember { mutableStateOf("") }
    var anidbUser by remember { mutableStateOf("") }
    var anidbPass by remember { mutableStateOf("") }
    var anidbKey by remember { mutableStateOf("") }

    SectionTitle("Metadata providers")
    for (mediaType in data.providers.chains.keys.sorted()) {
        ProviderOrderRow(mediaType, data.providers.chains.getValue(mediaType), viewModel)
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
        OutlinedTextField(
            value = tmdbKey,
            onValueChange = { tmdbKey = it },
            label = { Text(if (data.providers.tmdb.configured) "TMDB key (configured — replace)" else "TMDB API key") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Button(
            onClick = { viewModel.setTmdbKey(tmdbKey.trim()); tmdbKey = "" },
            enabled = tmdbKey.isNotBlank(),
            modifier = Modifier.padding(start = 8.dp),
        ) { Text("Save") }
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        OutlinedButton(
            onClick = { viewModel.enrichRun() },
            enabled = data.providers.tmdb.configured && !data.enrich.running,
        ) { Text(if (data.enrich.running) "Enriching…" else "Enrich now") }
        Text(
            "${data.enrich.matched} matched · ${data.enrich.weak} weak · ${data.enrich.missed} missed",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp),
        )
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
        OutlinedTextField(
            value = tvdbKey,
            onValueChange = { tvdbKey = it },
            label = { Text(if (data.providers.tvdb.configured) "TVDB key (configured — replace)" else "TVDB API key") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = tvdbPin,
            onValueChange = { tvdbPin = it },
            label = { Text("PIN") },
            singleLine = true,
            modifier = Modifier.weight(0.5f).padding(start = 8.dp),
        )
        Button(
            onClick = { viewModel.setTvdbKey(tvdbKey.trim(), tvdbPin.trim().ifBlank { null }); tvdbKey = ""; tvdbPin = "" },
            enabled = tvdbKey.isNotBlank(),
            modifier = Modifier.padding(start = 8.dp),
        ) { Text("Save") }
    }

    Column(modifier = Modifier.padding(top = 12.dp)) {
        OutlinedTextField(
            value = anidbUser,
            onValueChange = { anidbUser = it },
            label = { Text(if (data.providers.anidb.configured) "AniDB username (configured — replace)" else "AniDB username") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = anidbPass,
            onValueChange = { anidbPass = it },
            label = { Text("AniDB password") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
        OutlinedTextField(
            value = anidbKey,
            onValueChange = { anidbKey = it },
            label = { Text("UDP API key (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
        Button(
            onClick = {
                viewModel.setAnidb(anidbUser.trim(), anidbPass.trim(), anidbKey.trim().ifBlank { null })
                anidbUser = ""; anidbPass = ""; anidbKey = ""
            },
            enabled = anidbUser.isNotBlank() && anidbPass.isNotBlank(),
            modifier = Modifier.padding(top = 8.dp),
        ) { Text("Save") }
    }
}

@Composable
private fun ProviderOrderRow(mediaType: String, chain: ProviderChain, viewModel: AdminViewModel) {
    var draft by remember(chain.order) { mutableStateOf(chain.order) }
    val dirty = draft != chain.order

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(mediaType, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 8.dp))
        Column(modifier = Modifier.weight(1f)) {
            draft.forEachIndexed { index, provider ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${index + 1}. $provider", modifier = Modifier.weight(1f))
                    IconButton(
                        enabled = index > 0,
                        onClick = {
                            val next = draft.toMutableList()
                            next[index] = draft[index - 1]; next[index - 1] = provider
                            draft = next
                        },
                    ) { Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Higher precedence") }
                    IconButton(
                        enabled = index < draft.size - 1,
                        onClick = {
                            val next = draft.toMutableList()
                            next[index] = draft[index + 1]; next[index + 1] = provider
                            draft = next
                        },
                    ) { Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Lower precedence") }
                }
            }
        }
        if (dirty) {
            Button(onClick = { viewModel.setChain(mediaType, draft) }) { Text("Apply") }
            TextButton(onClick = { draft = chain.order }) { Text("Reset") }
        }
    }
}

// ---- Pending enrollments ----

@Composable
private fun EnrollmentsSection(pending: List<PendingEnrollment>, viewModel: AdminViewModel) {
    var code by remember { mutableStateOf("") }

    SectionTitle("Pending enrollments")
    if (pending.isEmpty()) {
        Text(
            "None. A new satellite prints its code on its console when it first starts.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        pending.forEach { p ->
            Text("${p.moduleType} · ${p.name} · ${p.moduleId}", style = MaterialTheme.typography.bodyMedium)
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        OutlinedTextField(
            value = code,
            onValueChange = { code = it },
            label = { Text("Enrollment code (XXXX-XXXX)") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Button(
            onClick = { viewModel.approve(code.trim()); code = "" },
            enabled = code.isNotBlank(),
            modifier = Modifier.padding(start = 8.dp),
        ) { Text("Approve") }
    }
}

// ---- Satellites ----

@Composable
private fun SatellitesSection(satellites: List<Satellite>, viewModel: AdminViewModel) {
    var confirmingDelete by remember { mutableStateOf<String?>(null) }

    SectionTitle("Satellites")
    if (satellites.isEmpty()) {
        Text(
            "No satellites enrolled.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    satellites.forEach { s ->
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AssistChip(onClick = {}, label = { Text(if (s.connected) "online" else "offline") })
                Text(s.moduleType, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 6.dp))
                Text(s.name, modifier = Modifier.padding(start = 6.dp).weight(1f))
                if (s.disabled) {
                    AssistChip(onClick = {}, label = { Text("disabled") })
                }
            }
            MeasuredFacts(s)
            Row {
                if (s.moduleType == "transcoder") {
                    TextButton(onClick = { viewModel.setSatelliteDisabled(s.moduleId, !s.disabled) }) {
                        Text(if (s.disabled) "Enable" else "Disable")
                    }
                }
                TextButton(
                    onClick = {
                        if (confirmingDelete == s.moduleId) {
                            viewModel.deleteSatellite(s.moduleId)
                            confirmingDelete = null
                        } else {
                            confirmingDelete = s.moduleId
                        }
                    },
                ) {
                    Text(
                        if (confirmingDelete == s.moduleId) "Really delete + revoke?" else "Delete",
                        color = if (confirmingDelete == s.moduleId) MaterialTheme.colorScheme.error else Color.Unspecified,
                    )
                }
            }
        }
        HorizontalDivider()
    }
}

/// What a box was MEASURED doing, under what it claims it can do — only
/// rendered when there's actually something measured (mirrors
/// `MeasuredFacts` in Admin.tsx:206-241).
@Composable
private fun MeasuredFacts(s: Satellite) {
    val caps = s.capabilities ?: return
    val parts = buildList {
        caps.encoders.forEach { e ->
            val speed = speedPair(e.speed1080, e.speed2160)
            add("${e.codec}${if (e.hardware) " hw" else ""}${speed?.let { " $it" } ?: ""}")
        }
        speedPair(caps.tonemapSpeed1080, caps.tonemapSpeed2160)?.let { add("tone-map $it") }
        if ((s.linkBytesPerSec ?: 0) > 0) add("link ${"%.1f".format(s.linkBytesPerSec!! / 1_000_000.0)} MB/s")
        s.pace.forEach { add("${it.cls} ${"%.1f".format(it.multiple)}×") }
    }
    if (parts.isEmpty()) return
    Text(
        parts.joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun speedPair(a: Double?, b: Double?): String? {
    val parts = listOfNotNull(a?.takeIf { it > 0 }, b?.takeIf { it > 0 }).map { "%.1f×".format(it) }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" / ")
}

// ---- Libraries ----

@Composable
private fun LibrariesSection(libraries: List<AdminLibrary>, collections: List<CollectionInfo>, viewModel: AdminViewModel) {
    var newName by remember { mutableStateOf("") }
    var newType by remember { mutableStateOf("movies") }
    var typeMenuExpanded by remember { mutableStateOf(false) }
    val mediaTypes = listOf("movies", "series", "anime", "music")

    SectionTitle("Libraries")
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = newName,
            onValueChange = { newName = it },
            label = { Text("New library name") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Box {
            OutlinedButton(onClick = { typeMenuExpanded = true }, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text(newType)
            }
            DropdownMenu(expanded = typeMenuExpanded, onDismissRequest = { typeMenuExpanded = false }) {
                mediaTypes.forEach { t ->
                    DropdownMenuItem(text = { Text(t) }, onClick = { newType = t; typeMenuExpanded = false })
                }
            }
        }
        Button(
            onClick = { viewModel.createLibrary(newName.trim(), newType); newName = "" },
            enabled = newName.isNotBlank(),
        ) { Text("Create") }
    }

    libraries.forEach { lib ->
        val attachable = collections.filter { c ->
            c.mediaType == lib.mediaType && lib.collections.none { it.moduleId == c.moduleId && it.collectionId == c.collectionId }
        }
        var attachMenuExpanded by remember(lib.id) { mutableStateOf(false) }

        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AssistChip(onClick = {}, label = { Text(lib.mediaType) })
                Text(lib.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 8.dp).weight(1f))
                TextButton(onClick = { viewModel.refreshLibrary(lib.id) }, enabled = lib.collections.isNotEmpty()) {
                    Text("Refresh")
                }
                TextButton(onClick = { viewModel.deleteLibrary(lib.id) }) { Text("Delete") }
            }
            lib.collections.forEach { m ->
                val info = collections.find { it.moduleId == m.moduleId && it.collectionId == m.collectionId }
                val scan = info?.scan
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp)) {
                    Text(
                        (m.hostName ?: m.moduleId) + "/" + m.collectionId + (if (info != null && !info.connected) " (offline)" else "") +
                            (scan?.let { " · ${if (it.complete) "scanned" else "scanning"} ${it.scanned}" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { viewModel.detachCollection(lib.id, m.moduleId, m.collectionId) }) { Text("×") }
                }
            }
            if (attachable.isNotEmpty()) {
                Box(modifier = Modifier.padding(start = 8.dp, top = 4.dp)) {
                    TextButton(onClick = { attachMenuExpanded = true }) { Text("attach…") }
                    DropdownMenu(expanded = attachMenuExpanded, onDismissRequest = { attachMenuExpanded = false }) {
                        attachable.forEach { c ->
                            DropdownMenuItem(
                                text = { Text((c.hostName ?: c.moduleId) + "/" + c.collectionId) },
                                onClick = {
                                    viewModel.attachCollection(lib.id, c.moduleId, c.collectionId)
                                    attachMenuExpanded = false
                                },
                            )
                        }
                    }
                }
            }
        }
        HorizontalDivider()
    }
}

// ---- Active sessions ----

@Composable
private fun SessionsSection(sessions: List<AdminSession>, viewModel: AdminViewModel) {
    SectionTitle("Active sessions")
    if (sessions.isEmpty()) {
        Text(
            "Nobody is streaming.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    sessions.forEach { s ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AssistChip(onClick = {}, label = { Text(s.mode) })
                    Text(s.title ?: s.sessionId, modifier = Modifier.padding(start = 6.dp))
                }
                Text(
                    listOfNotNull(
                        s.streams?.let { "v: ${it.video ?: "?"} · a: ${it.audio ?: "?"}" },
                        s.username,
                        "idle ${s.idleSecs}s",
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { viewModel.endSession(s.sessionId) }) { Text("End") }
        }
        HorizontalDivider()
    }
}
