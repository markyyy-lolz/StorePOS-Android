package com.storepos.launcher.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val StorePosDark = darkColorScheme(
    primary = Color(0xFF27A7FF),
    onPrimary = Color(0xFF001B2A),
    primaryContainer = Color(0xFF0C3450),
    onPrimaryContainer = Color(0xFFC7E8FF),
    secondary = Color(0xFF26D4C4),
    tertiary = Color(0xFF42DFB3),
    background = Color(0xFF071019),
    onBackground = Color(0xFFF0F6FA),
    surface = Color(0xFF0B1722),
    onSurface = Color(0xFFF0F6FA),
    surfaceContainer = Color(0xFF101F2B),
    surfaceContainerHigh = Color(0xFF142735),
    onSurfaceVariant = Color(0xFFAFC0CC)
)

@Composable
fun StorePosLauncherTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = StorePosDark,
        content = content
    )
}
