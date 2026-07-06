package com.prtracker.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Colors = darkColorScheme(
    primary = Color(0xFFFFC857),
    secondary = Color(0xFFFF7A1A),
    tertiary = Color(0xFFE11D18),
    background = Color(0xFF080403),
    surface = Color(0xCC160B08),
    surfaceVariant = Color(0xD9261612),
    error = Color(0xFFFF4D2E),
    onPrimary = Color(0xFF1A0903),
    onSecondary = Color(0xFF160603),
    onTertiary = Color.White,
    onBackground = Color(0xFFFFF3DF),
    onSurface = Color(0xFFFFF3DF),
    onSurfaceVariant = Color(0xFFFFC98A),
    outline = Color(0xFFFF8A2A),
)

@Composable
fun PRTrackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Colors,
        typography = MaterialTheme.typography,
        content = content,
    )
}
