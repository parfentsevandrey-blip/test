package dev.halo.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Background = Color(0xFF0B0D12)
val Card = Color(0xFF141821)
val Accent = Color(0xFF8FB4FF)
val Online = Color(0xFF4ADE80)
val Muted = Color(0xFF7C8496)
val Danger = Color(0xFFFF7A7A)

@Composable
fun HaloTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            onPrimary = Background,
            background = Background,
            surface = Card,
            surfaceContainerHigh = Card,
            error = Danger,
        ),
        content = content,
    )
}
