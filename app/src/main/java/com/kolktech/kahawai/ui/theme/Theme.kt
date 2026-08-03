package com.kolktech.kahawai.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Matches the kahawai hub's own web UI (colors sampled directly from its
// screens): a near-black teal background, a slightly-lighter teal-slate
// surface tone for cards, a mint-teal primary for buttons/focus/CTAs (the
// hub's "Play" button and watched checkmarks), and a pale warm gold used
// only for progress bars and "new episode" badges — never for buttons.
val KahawaiPrimary = Color(0xFF3ECDA0)
val KahawaiAmber = Color(0xFFDCB877)
val KahawaiBackground = Color(0xFF0D191C)
val KahawaiSurface = Color(0xFF122024)
val KahawaiSurfaceVariant = Color(0xFF1B2C30)
val KahawaiOnSurface = Color(0xFFD9E6E6)
val KahawaiOnSurfaceVariant = Color(0xFF7E9598)
val KahawaiOutline = Color(0xFF243A3F)

private val KahawaiColorScheme = darkColorScheme(
    primary = KahawaiPrimary,
    onPrimary = Color(0xFF06231D),
    secondary = KahawaiAmber,
    onSecondary = Color(0xFF2B2210),
    background = KahawaiBackground,
    onBackground = KahawaiOnSurface,
    surface = KahawaiSurface,
    onSurface = KahawaiOnSurface,
    surfaceVariant = KahawaiSurfaceVariant,
    onSurfaceVariant = KahawaiOnSurfaceVariant,
    outline = KahawaiOutline,
)

@Composable
fun KahawaiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KahawaiColorScheme,
        content = content,
    )
}
