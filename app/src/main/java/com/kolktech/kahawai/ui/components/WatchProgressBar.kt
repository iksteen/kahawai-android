package com.kolktech.kahawai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/// Thin in-progress line along the bottom edge of an item's artwork.
/// Renders nothing unless the item is genuinely mid-watch: fully watched
/// items already get their own "Watched" treatment, and a full-width bar
/// on them would read as "almost done" rather than "seen it".
@Composable
fun WatchProgressBar(
    positionMs: Long?,
    durationMs: Long?,
    played: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (played) return
    val position = positionMs ?: return
    val duration = durationMs ?: return
    if (position <= 0 || duration <= 0) return
    val fraction = (position.toFloat() / duration).coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .background(Color.Black.copy(alpha = 0.6f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}
