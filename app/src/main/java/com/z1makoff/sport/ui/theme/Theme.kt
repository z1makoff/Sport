package com.z1makoff.sport.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Accent = Color(0xFFFF6B2C)
val Ahead = Color(0xFF3DDC84)
val Behind = Color(0xFFFF5A5F)

private val Colors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF1A0A00),
    secondary = Color(0xFF9FB3C8),
    background = Color(0xFF0F1319),
    onBackground = Color(0xFFE8EDF3),
    surface = Color(0xFF0F1319),
    onSurface = Color(0xFFE8EDF3),
    surfaceVariant = Color(0xFF1A212B),
    onSurfaceVariant = Color(0xFF98A4B3),
    surfaceContainer = Color(0xFF1A212B),
    surfaceContainerHigh = Color(0xFF222B37),
    outline = Color(0xFF3A4555),
    error = Behind,
)

@Composable
fun SportTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
