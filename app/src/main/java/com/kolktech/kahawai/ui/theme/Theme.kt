package com.kolktech.kahawai.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Matches the kahawai hub's own web UI: dark navy background, a
// teal-slate surface tone for cards/placeholders, a warm amber accent for
// primary actions and progress, and a mint-teal secondary used for
// content-type accents.
private val KahawaiAccent = Color(0xFFD9A05B)
private val KahawaiSecondary = Color(0xFF3ECDA0)
private val KahawaiBackground = Color(0xFF0A0E12)
private val KahawaiSurface = Color(0xFF141B1F)

private val KahawaiColorScheme = darkColorScheme(
    primary = KahawaiAccent,
    secondary = KahawaiSecondary,
    background = KahawaiBackground,
    surface = KahawaiSurface,
)

@Composable
fun KahawaiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KahawaiColorScheme,
        content = content,
    )
}
