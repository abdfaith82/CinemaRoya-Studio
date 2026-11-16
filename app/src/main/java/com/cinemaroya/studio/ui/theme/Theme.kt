package com.cinemaroya.studio.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

private val CineRoyaDarkColorScheme = darkColorScheme(
    primary = CinemaGold,
    onPrimary = Color(0xFF090D16),
    primaryContainer = Color(0xFF3B2806),
    onPrimaryContainer = CinemaGoldLight,
    secondary = StudioCyan,
    onSecondary = Color(0xFF041E24),
    secondaryContainer = Color(0xFF0A3641),
    onSecondaryContainer = StudioCyanLight,
    tertiary = StudioEmerald,
    onTertiary = Color(0xFF032218),
    background = StudioObsidian,
    onBackground = TextPrimaryLight,
    surface = StudioSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = StudioCard,
    onSurfaceVariant = TextSecondaryMuted,
    outline = DividerSubtle,
    error = StudioCoral,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = CineRoyaDarkColorScheme,
            typography = Typography,
            content = content
        )
    }
}
