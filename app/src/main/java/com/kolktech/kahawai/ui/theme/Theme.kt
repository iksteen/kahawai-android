package com.kolktech.kahawai.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val KahawaiAccent = Color(0xFFD98E48)
private val KahawaiBackground = Color(0xFF0B0E11)
private val KahawaiSurface = Color(0xFF15191E)

private val KahawaiColorScheme = darkColorScheme(
    primary = KahawaiAccent,
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
