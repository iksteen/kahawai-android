package com.kolktech.kahawai.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil3.compose.AsyncImage
import com.kolktech.kahawai.R
import com.kolktech.kahawai.data.network.dto.Item
import com.kolktech.kahawai.data.network.dto.ItemDetail
import com.kolktech.kahawai.data.repository.CatalogRepository

/// Containers with no media of their own — you drill into a child
/// (episode/track) to get a Play button.
private val NOT_DIRECTLY_PLAYABLE = setOf("show", "album")

@Composable
fun DetailScreen(
    itemId: String,
    repo: CatalogRepository,
    onOpenItem: (String) -> Unit,
    onPlay: (itemId: String, startMs: Long) -> Unit,
) {
    val viewModel: DetailViewModel = viewModel(
        key = itemId,
        factory = viewModelFactory { initializer { DetailViewModel(repo, itemId) } },
    )
    val state by viewModel.state.collectAsState()

    when (val s = state) {
        is DetailState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is DetailState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(s.message, color = MaterialTheme.colorScheme.error)
                Button(onClick = { viewModel.load() }, modifier = Modifier.padding(top = 12.dp)) {
                    Text("Retry")
                }
            }
        }
        is DetailState.Loaded -> DetailContent(s.detail, s.children, repo, onOpenItem, onPlay)
    }
}

@Composable
private fun DetailContent(
    detail: ItemDetail,
    children: List<Item>,
    repo: CatalogRepository,
    onOpenItem: (String) -> Unit,
    onPlay: (itemId: String, startMs: Long) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
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
                Text(detail.title, style = MaterialTheme.typography.headlineSmall)
                val meta = listOfNotNull(
                    detail.year?.toString(),
                    detail.season?.let { "S$it" }
                        ?.plus(detail.episode?.let { "E$it" } ?: ""),
                    detail.parentTitle,
                ).joinToString(" · ")
                if (meta.isNotBlank()) {
                    Text(
                        meta,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                ResumeLine(detail)
                if (detail.kind !in NOT_DIRECTLY_PLAYABLE) {
                    Button(
                        onClick = { onPlay(detail.id, detail.resumePositionMs ?: 0) },
                        modifier = Modifier.padding(top = 12.dp),
                    ) {
                        Text(if ((detail.resumePositionMs ?: 0) > 0) "Resume" else "Play")
                    }
                }
                detail.metadata?.overview?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
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
