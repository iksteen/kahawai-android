package com.kolktech.kahawai.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.kolktech.kahawai.KahawaiApp
import com.kolktech.kahawai.ui.nav.KahawaiNavGraph
import com.kolktech.kahawai.ui.theme.KahawaiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideStatusBar()
        val app = application as KahawaiApp
        setContent {
            KahawaiTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    // Safe-drawing (cutout) padding is applied inside the
                    // nav graph, per route — the player deliberately goes
                    // without it to draw into the camera cutout.
                    KahawaiNavGraph(app)
                }
            }
        }
    }

    /// The system can bring the status bar back on its own across focus
    /// transitions (IME, dialogs, returning from another app), so the hide
    /// is reapplied whenever this window regains focus rather than trusting
    /// the one call in onCreate.
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideStatusBar()
    }

    /// Status bar only, app-wide, both orientations. The navigation bar
    /// stays — only the player screen takes that away too, managing it
    /// itself for exactly as long as it's on-screen (see PlayerScreen).
    /// A swipe from the top edge reveals the bar transiently, then it
    /// hides itself again.
    private fun hideStatusBar() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.statusBars())
    }
}
