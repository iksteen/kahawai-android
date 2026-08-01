package com.kolktech.kahawai.ui.player

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.ui.PlayerView
import com.kolktech.kahawai.data.repository.PlaybackRepository

@Composable
fun PlayerScreen(
    itemId: String,
    startMs: Long,
    onClose: () -> Unit,
) {
    val application = LocalContext.current.applicationContext as Application
    val repo = remember { PlaybackRepository() }
    val viewModel: PlayerViewModel = viewModel(
        key = itemId,
        factory = viewModelFactory { initializer { PlayerViewModel(application, repo, itemId, startMs) } },
    )
    val state by viewModel.state.collectAsState()

    BackHandler(onBack = onClose)

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        when (val s = state) {
            is PlayerState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            is PlayerState.Error -> Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(s.message, color = MaterialTheme.colorScheme.error)
                Button(onClick = onClose, modifier = Modifier.padding(top = 12.dp)) {
                    Text("Back")
                }
            }
            is PlayerState.Ready -> AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = viewModel.player
                        useController = true
                    }
                },
            )
        }
    }
}
