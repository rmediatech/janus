package com.rmediatech.janus.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val DarkCyberBg = Color(0xFF0A0E17)
val DarkSurface = Color(0xFF111827)
val DarkCard = Color(0xFF1F2937)
val CyberCyan = Color(0xFF00F5FF)
val CyberAmber = Color(0xFFF59E0B)
val TextMain = Color(0xFFF9FAFB)
val TextMuted = Color(0xFF9CA3AF)

private val DarkColors = darkColorScheme(
    primary = CyberCyan,
    secondary = CyberAmber,
    background = DarkCyberBg,
    surface = DarkSurface,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = TextMain,
    onSurface = TextMain
)

@Composable
fun JanusTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content
    )
}
