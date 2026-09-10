package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val MovieRoomDarkColorScheme = darkColorScheme(
    primary = CinematicRed,
    onPrimary = Color.White,
    primaryContainer = CinematicRedDark,
    onPrimaryContainer = Color.White,
    secondary = AmberGold,
    onSecondary = ObsidianBlack,
    tertiary = ElectricBlue,
    onTertiary = ObsidianBlack,
    background = ObsidianBlack,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = DividerDark
)

@Composable
fun MovieRoomTheme(
    darkTheme: Boolean = true, // Streaming apps default to dark cinematic mode
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = MovieRoomDarkColorScheme,
        typography = Typography,
        content = content
    )
}
