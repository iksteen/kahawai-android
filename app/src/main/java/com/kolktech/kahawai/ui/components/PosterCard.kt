package com.kolktech.kahawai.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kolktech.kahawai.R
import com.kolktech.kahawai.data.network.dto.Item
import com.kolktech.kahawai.data.repository.CatalogRepository

@Composable
fun PosterCard(
    item: Item,
    repo: CatalogRepository,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    // D-pad navigation has no hover/pressed state to lean on, so the
    // poster needs its own clearly visible affordance on focus — a scale
    // pop plus a colored border, matching the standard TV card treatment.
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "posterFocusScale")
    // Requested from here rather than by the caller: a focusRequester
    // isn't usable until the node it's attached to actually exists, and
    // inside a lazy list that only happens once this composable itself
    // runs — a caller-side LaunchedEffect can fire a frame too early,
    // before this item has been composed, and silently no-op.
    if (focusRequester != null) {
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick(item.id) },
    ) {
        AsyncImage(
            model = repo.artworkUrl(item.id, item.artVersion, "card"),
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            // No local cover, unmatched metadata, or a transient fetch
            // failure all land here (the hub 404s rather than erroring) —
            // same fallback whether it's still loading or gave up.
            placeholder = painterResource(R.drawable.placeholder_poster),
            error = painterResource(R.drawable.placeholder_poster),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(RoundedCornerShape(6.dp))
                .border(
                    width = if (focused) 3.dp else 0.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(6.dp),
                ),
        )
        Text(
            item.title,
            style = MaterialTheme.typography.bodySmall,
            color = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
