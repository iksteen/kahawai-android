package com.kolktech.kahawai.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kolktech.kahawai.data.network.dto.Item
import com.kolktech.kahawai.data.repository.CatalogRepository
import com.kolktech.kahawai.ui.components.ErrorView
import com.kolktech.kahawai.ui.components.PosterCard
import com.kolktech.kahawai.ui.theme.KahawaiOnSurfaceVariant

private val POSTER_MIN_WIDTH = 100.dp
private val GRID_SPACING = 10.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repo: CatalogRepository,
    onOpenItem: (String) -> Unit,
    onOpenLibrary: (id: String, name: String) -> Unit,
    onSearch: () -> Unit,
    onSessionExpired: () -> Unit,
) {
    val viewModel: HomeViewModel = viewModel(
        factory = viewModelFactory { initializer { HomeViewModel(repo) } },
    )
    val state by viewModel.state.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "kahawai",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                actions = {
                    IconButton(onClick = onSearch) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Search",
                            tint = KahawaiOnSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                is HomeState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is HomeState.Error -> ErrorView(
                    message = s.message,
                    isAuthError = s.isAuthError,
                    onRetry = { viewModel.load() },
                    onSignInAgain = onSessionExpired,
                )
                is HomeState.Loaded -> {
                    if (s.rows.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No libraries yet. Connect a mediahost to the hub.")
                        }
                    } else {
                        // The very first poster on screen needs a D-pad
                        // focus target the instant the grid appears —
                        // otherwise there's nothing highlighted for a TV
                        // remote to act on until the user presses a key.
                        // PosterCard requests its own focus once composed
                        // (see its focusRequester handling) rather than
                        // this scope guessing when that's safe to do.
                        val firstItemFocusRequester = remember { FocusRequester() }

                        // Column count is derived once from the measured
                        // width (so landscape/tablet fills the row instead
                        // of showing two oversized posters), then reused
                        // for every library's chunking below.
                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            val columns = ((maxWidth + GRID_SPACING) / (POSTER_MIN_WIDTH + GRID_SPACING))
                                .toInt()
                                .coerceAtLeast(2)
                            // Each grid row of ~[columns] posters is its own
                            // LazyColumn item (not one item per whole
                            // library) so scrolling a library into view
                            // only has to compose/measure and kick off
                            // Coil requests for the couple of poster rows
                            // actually entering the viewport per frame,
                            // rather than all ROW_SIZE posters in that
                            // library at once — that all-at-once burst was
                            // the cause of the scroll stutter.
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                s.rows.forEachIndexed { rowIndex, row ->
                                    item(key = "${row.library.id}-header") {
                                        LibraryHeader(row, onOpenLibrary)
                                    }
                                    itemsIndexed(
                                        row.items.chunked(columns),
                                        key = { _, chunk -> "${row.library.id}-${chunk.first().id}" },
                                    ) { chunkIndex, chunk ->
                                        PosterGridRow(
                                            chunk,
                                            columns,
                                            repo,
                                            onOpenItem,
                                            firstItemFocusRequester = if (rowIndex == 0 && chunkIndex == 0) {
                                                firstItemFocusRequester
                                            } else {
                                                null
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryHeader(
    row: LibraryRow,
    onOpenLibrary: (id: String, name: String) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp, start = 16.dp, end = 4.dp, bottom = 8.dp),
    ) {
        Text(
            row.library.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = KahawaiOnSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp).size(16.dp),
        )
        Spacer(modifier = Modifier.weight(1f))
        if (row.total > row.items.size) {
            TextButton(onClick = { onOpenLibrary(row.library.id, row.library.name) }) {
                Text(
                    "see all (${row.total})",
                    style = MaterialTheme.typography.bodySmall,
                    color = KahawaiOnSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PosterGridRow(
    chunk: List<Item>,
    columns: Int,
    repo: CatalogRepository,
    onOpenItem: (String) -> Unit,
    firstItemFocusRequester: FocusRequester? = null,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(GRID_SPACING),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        chunk.forEachIndexed { index, item ->
            PosterCard(
                item,
                repo,
                onOpenItem,
                modifier = Modifier.weight(1f),
                focusRequester = if (index == 0) firstItemFocusRequester else null,
            )
        }
        repeat(columns - chunk.size) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}
