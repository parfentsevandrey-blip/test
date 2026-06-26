package com.monthcalendar.widget.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val IosRed = Color(0xFFFF3B30)

private val Light = lightColorScheme(primary = IosRed, background = Color(0xFFF2F2F7), surface = Color.White)
private val Dark = darkColorScheme(primary = Color(0xFFFF453A), background = Color(0xFF000000), surface = Color(0xFF1C1C1E))

@Composable
fun CalendarTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) Dark else Light, content = content)
}
