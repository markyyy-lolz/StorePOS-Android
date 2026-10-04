package com.storepos.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF3B82F6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF132D4F),
    onPrimaryContainer = Color(0xFFD7E9FF),
    secondary = Color(0xFF67E8F9),
    background = Color(0xFF0B1016),
    surface = Color(0xFF111820),
    surfaceVariant = Color(0xFF18222D),
    onBackground = Color(0xFFF4F7FA),
    onSurface = Color(0xFFF4F7FA),
    outline = Color(0xFF2A3947),
    error = Color(0xFFFF6B6B)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1769E0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E8FF),
    secondary = Color(0xFF006879),
    background = Color(0xFFF6F8FB),
    surface = Color.White,
    surfaceVariant = Color(0xFFEDF1F5),
    onBackground = Color(0xFF101820),
    onSurface = Color(0xFF101820),
    outline = Color(0xFFD2D9E1)
)

@Composable
fun StorePosTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = MotoTypography,
        content = content
    )
}
