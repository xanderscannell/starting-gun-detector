package com.xanderscannell.startinggundetector.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ColorScheme = darkColorScheme(
    primary = Color(0xFFE53935),
    onPrimary = Color.White,
    secondary = Color(0xFF757575),
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    onBackground = Color.White,
    onSurface = Color.White,
    surfaceTint = Color.Transparent,
    secondaryContainer = Color(0xFF141414),
    onSecondaryContainer = Color(0xFFE53935)
)

@Composable
fun StartingGunTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColorScheme,
        content = content
    )
}
