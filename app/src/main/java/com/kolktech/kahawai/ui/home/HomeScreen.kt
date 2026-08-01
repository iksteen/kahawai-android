package com.kolktech.kahawai.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kolktech.kahawai.data.repository.CatalogRepository
import com.kolktech.kahawai.ui.components.PosterCard

@Composable
fun HomeScreen(
    repo: CatalogRepository,
    onOpenItem: (String) -> Unit,
) {
    val viewModel: HomeViewModel = viewModel(
        factory = viewModelFactory { initializer { HomeViewModel(repo) } },
    )
    val state by viewModel.state.collectAsState()

    when (val s = state) {
        is HomeState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is HomeState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(s.message, color = MaterialTheme.colorScheme.error)
                Button(onClick = { viewModel.load() }, modifier = Modifier.padding(top = 12.dp)) {
                    Text("Retry")
                }
            }
        }
        is HomeState.Loaded -> {
            if (s.rows.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No libraries yet. Connect a mediahost to the hub.")
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(s.rows, key = { it.library.id }) { row ->
                        LibraryRowSection(row, repo, onOpenItem)
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryRowSection(
    row: LibraryRow,
    repo: CatalogRepository,
    onOpenItem: (String) -> Unit,
) {
    Column(modifier = Modifier.padding(top = 16.dp)) {
        Text(
            row.library.name,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            items(row.items, key = { it.id }) { item ->
                PosterCard(item, repo, onOpenItem)
            }
        }
    }
}
