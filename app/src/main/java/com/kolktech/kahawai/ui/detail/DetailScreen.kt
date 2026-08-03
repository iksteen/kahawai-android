package com.kolktech.kahawai.ui.detail

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil3.compose.AsyncImage
import com.kolktech.kahawai.R
import com.kolktech.kahawai.data.network.dto.AudioStreamInfo
import com.kolktech.kahawai.data.network.dto.Item
import com.kolktech.kahawai.data.network.dto.ItemDetail
import com.kolktech.kahawai.data.network.dto.SubtitleTrack
import com.kolktech.kahawai.data.network.dto.VideoStreamInfo
import com.kolktech.kahawai.data.network.dto.displayLabel
import com.kolktech.kahawai.data.repository.CatalogRepository
import com.kolktech.kahawai.ui.components.ErrorView

@Composable
fun DetailScreen(
    itemId: String,
    repo: CatalogRepository,
    onOpenItem: (String) -> Unit,
    onPlay: (itemId: String, startMs: Long, audioTrack: Int, subtitleTrackId: Long?) -> Unit,
    onBack: () -> Unit,
    onSessionExpired: () -> Unit,
) {
    val application = LocalContext.current.applicationContext as Application
    val viewModel: DetailViewModel = viewModel(
        key = itemId,
        factory = viewModelFactory { initializer { DetailViewModel(application, repo, itemId) } },
    )
    val state by viewModel.state.collectAsState()

    Box(Modifier.fillMaxSize()) {
        when (val s = state) {
            is DetailState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is DetailState.Error -> ErrorView(
                message = s.message,
                isAuthError = s.isAuthError,
                onRetry = { viewModel.load() },
                onSignInAgain = onSessionExpired,
            )
            is DetailState.Loaded -> DetailContent(
                state = s,
                repo = repo,
                onOpenItem = onOpenItem,
                onPlay = onPlay,
                onSelectAudioTrack = viewModel::selectAudioTrackIndex,
                onSelectSubtitleTrack = viewModel::selectSubtitleTrack,
            )
        }
        // Floating over the hero image (same treatment as the player's
        // own back button) rather than a full TopAppBar, which would
        // shove the artwork down and duplicate the title shown right
        // below it.
        Surface(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.5f),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                modifier = Modifier.padding(10.dp),
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun DetailContent(
    state: DetailState.Loaded,
    repo: CatalogRepository,
    onOpenItem: (String) -> Unit,
    onPlay: (itemId: String, startMs: Long, audioTrack: Int, subtitleTrackId: Long?) -> Unit,
    onSelectAudioTrack: (Int) -> Unit,
    onSelectSubtitleTrack: (SubtitleTrack?) -> Unit,
) {
    val detail = state.detail
    val children = state.children
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val primarySource = detail.sources.firstOrNull()
    val audioTracks = primarySource?.streams?.audio.orEmpty()
    val videoStream = primarySource?.streams?.video?.firstOrNull()
    val runtimeMs = primarySource?.streams?.durationMs ?: detail.resumeDurationMs

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            if (isLandscape) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    AsyncImage(
                        model = repo.artworkUrl(detail.id, detail.artVersion, "card"),
                        contentDescription = detail.title,
                        contentScale = ContentScale.Crop,
                        placeholder = painterResource(R.drawable.placeholder_poster),
                        error = painterResource(R.drawable.placeholder_poster),
                        modifier = Modifier
                            .weight(0.4f)
                            .aspectRatio(2f / 3f),
                    )
                    Column(modifier = Modifier.weight(0.6f).padding(start = 16.dp)) {
                        DetailInfo(
                            detail = detail,
                            audioTracks = audioTracks,
                            videoStream = videoStream,
                            runtimeMs = runtimeMs,
                            subtitleTracks = state.subtitleTracks,
                            selectedAudioTrackIndex = state.selectedAudioTrackIndex,
                            selectedSubtitleTrack = state.selectedSubtitleTrack,
                            onSelectAudioTrack = onSelectAudioTrack,
                            onSelectSubtitleTrack = onSelectSubtitleTrack,
                            onPlay = onPlay,
                        )
                    }
                }
            } else {
                Column {
                    AsyncImage(
                        model = repo.artworkUrl(detail.id, detail.artVersion),
                        contentDescription = detail.title,
                        contentScale = ContentScale.Crop,
                        placeholder = painterResource(R.drawable.placeholder_poster),
                        error = painterResource(R.drawable.placeholder_poster),
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f),
                    )
                    Column(modifier = Modifier.padding(16.dp)) {
                        DetailInfo(
                            detail = detail,
                            audioTracks = audioTracks,
                            videoStream = videoStream,
                            runtimeMs = runtimeMs,
                            subtitleTracks = state.subtitleTracks,
                            selectedAudioTrackIndex = state.selectedAudioTrackIndex,
                            selectedSubtitleTrack = state.selectedSubtitleTrack,
                            onSelectAudioTrack = onSelectAudioTrack,
                            onSelectSubtitleTrack = onSelectSubtitleTrack,
                            onPlay = onPlay,
                        )
                    }
                }
            }
        }
        if (children.isNotEmpty()) {
            items(children, key = { it.id }) { child ->
                ChildRow(child, onOpenItem)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun DetailInfo(
    detail: ItemDetail,
    audioTracks: List<AudioStreamInfo>,
    videoStream: VideoStreamInfo?,
    runtimeMs: Long?,
    subtitleTracks: List<SubtitleTrack>,
    selectedAudioTrackIndex: Int,
    selectedSubtitleTrack: SubtitleTrack?,
    onSelectAudioTrack: (Int) -> Unit,
    onSelectSubtitleTrack: (SubtitleTrack?) -> Unit,
    onPlay: (itemId: String, startMs: Long, audioTrack: Int, subtitleTrackId: Long?) -> Unit,
) {
    Text(detail.title, style = MaterialTheme.typography.headlineSmall)
    val meta = listOfNotNull(
        detail.year?.toString(),
        detail.season?.let { "S$it" }
            ?.plus(detail.episode?.let { "E$it" } ?: ""),
        detail.parentTitle,
    ).joinToString(" · ")
    if (meta.isNotBlank()) {
        Text(meta, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
    }

    val facts = listOfNotNull(
        detail.metadata?.rating?.takeIf { it > 0 }?.let { "★ %.1f".format(it) },
        runtimeMs?.takeIf { it > 0 }?.let { formatRuntime(it) },
        videoStream?.resolutionLabel(),
        videoStream?.let { it.codec.uppercase() },
        videoStream?.hdr?.uppercase(),
    ).joinToString("  ·  ")
    if (facts.isNotBlank()) {
        Text(facts, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
    }
    detail.metadata?.genres?.takeIf { it.isNotEmpty() }?.let {
        Text(it.joinToString(", "), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
    }

    ResumeLine(detail)

    if (detail.kind !in NOT_DIRECTLY_PLAYABLE) {
        Button(
            onClick = {
                onPlay(
                    detail.id,
                    detail.resumePositionMs ?: 0,
                    selectedAudioTrackIndex,
                    selectedSubtitleTrack?.id,
                )
            },
            modifier = Modifier.padding(top = 12.dp),
        ) {
            Text(if ((detail.resumePositionMs ?: 0) > 0) "Resume" else "Play")
        }

        if (audioTracks.size > 1) {
            TrackPicker(
                title = "Audio",
                modifier = Modifier.padding(top = 16.dp),
            ) {
                audioTracks.forEachIndexed { index, track ->
                    SelectableRow(
                        text = track.displayLabel(),
                        selected = index == selectedAudioTrackIndex,
                        onClick = { onSelectAudioTrack(index) },
                    )
                }
            }
        }

        if (subtitleTracks.isNotEmpty()) {
            SubtitlePicker(
                tracks = subtitleTracks,
                selected = selectedSubtitleTrack,
                onSelect = onSelectSubtitleTrack,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }

    detail.metadata?.overview?.let {
        Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 16.dp))
    }
}

@Composable
private fun TrackPicker(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier = modifier) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        content()
    }
}

@Composable
private fun SubtitlePicker(
    tracks: List<SubtitleTrack>,
    selected: SubtitleTrack?,
    onSelect: (SubtitleTrack?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        Text("Subtitles", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 4.dp))
        OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
            Text(selected?.displayLabel() ?: "Off", modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
        }
    }
    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = { showPicker = false }) { Text("Close") }
            },
            title = { Text("Subtitles") },
            text = {
                // A capped height so the popup itself stays small and
                // scrolls, instead of growing to fit every track.
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    item {
                        SelectableRow(
                            text = "Off",
                            selected = selected == null,
                            onClick = { onSelect(null); showPicker = false },
                        )
                    }
                    items(tracks, key = { it.id }) { track ->
                        SelectableRow(
                            text = track.displayLabel(),
                            selected = selected?.id == track.id,
                            enabled = track.delivery != "none",
                            onClick = { onSelect(track); showPicker = false },
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun SelectableRow(text: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 4.dp),
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) LocalContentColor.current else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

private fun formatRuntime(ms: Long): String {
    val totalMinutes = ms / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

private fun VideoStreamInfo.resolutionLabel(): String = when {
    height >= 2160 -> "4K"
    height >= 1080 -> "1080p"
    height >= 720 -> "720p"
    height >= 480 -> "480p"
    height > 0 -> "${height}p"
    else -> ""
}

private fun AudioStreamInfo.displayLabel(): String {
    val channelLabel = when (channels) {
        1 -> "Mono"
        2 -> "Stereo"
        6 -> "5.1"
        8 -> "7.1"
        0 -> null
        else -> "${channels}ch"
    }
    return listOfNotNull(language?.uppercase(), codec.uppercase(), channelLabel).joinToString(" · ")
}

@Composable
private fun ResumeLine(detail: ItemDetail) {
    val resumeMs = detail.resumePositionMs
    val durationMs = detail.resumeDurationMs
    val text = when {
        detail.played -> "Watched"
        resumeMs != null && resumeMs > 0 -> {
            val minutes = resumeMs / 60_000
            "Resume at ${minutes}m" + if (durationMs != null && durationMs > 0) {
                " (${(resumeMs * 100 / durationMs)}%)"
            } else {
                ""
            }
        }
        else -> null
    }
    text?.let {
        Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun ChildRow(child: Item, onOpenItem: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenItem(child.id) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        val label = if (child.kind == "episode") {
            listOfNotNull(
                child.season?.let { "S$it" }?.plus(child.episode?.let { "E$it" } ?: ""),
                child.title,
            ).joinToString(" · ")
        } else {
            child.title
        }
        Text(label, style = MaterialTheme.typography.bodyLarge)
        if (child.playCount > 0 || child.played) {
            Text(
                "Watched",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
