package com.kolktech.kahawai.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.kolktech.kahawai.data.repository.CatalogRepository
import com.kolktech.kahawai.ui.home.HomeScreen
import com.kolktech.kahawai.ui.search.SearchScreen

private enum class Tab(val label: String) {
    HOME("Home"),
    SEARCH("Search"),
}

@Composable
fun MainScreen(
    repo: CatalogRepository,
    onOpenItem: (String) -> Unit,
) {
    var tab by remember { mutableStateOf(Tab.HOME) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == Tab.HOME,
                    onClick = { tab = Tab.HOME },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text(Tab.HOME.label) },
                )
                NavigationBarItem(
                    selected = tab == Tab.SEARCH,
                    onClick = { tab = Tab.SEARCH },
                    icon = { Icon(Icons.Default.Search, contentDescription = null) },
                    label = { Text(Tab.SEARCH.label) },
                )
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                Tab.HOME -> HomeScreen(repo, onOpenItem)
                Tab.SEARCH -> SearchScreen(repo, onOpenItem)
            }
        }
    }
}
