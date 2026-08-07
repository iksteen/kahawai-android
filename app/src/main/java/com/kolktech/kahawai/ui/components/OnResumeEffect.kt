package com.kolktech.kahawai.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/// Runs [action] every time this screen returns to the foreground —
/// i.e. when the nav entry on top of it is popped (back from the
/// player) or the app itself is foregrounded. Catalog screens hook
/// their ViewModel's refresh() here: their ViewModels are retained by
/// the back-stack entry and only load in init{}, so without this the
/// resume positions and watch-progress bars they show would stay frozen
/// at whatever was true when the screen was FIRST opened. ON_RESUME
/// also fires on initial entry, right after init{}'s own load starts —
/// each refresh() guards itself against that (skip unless Loaded).
@Composable
fun OnResumeEffect(action: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentAction by rememberUpdatedState(action)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) currentAction()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
