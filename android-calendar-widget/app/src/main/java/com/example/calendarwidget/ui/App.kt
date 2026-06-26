package com.example.calendarwidget.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.calendarwidget.data.CalendarMath
import com.example.calendarwidget.ui.theme.AppBgBottom
import com.example.calendarwidget.ui.theme.AppBgTop
import com.example.calendarwidget.ui.theme.Manrope

private enum class Screen { Day, Settings }

/**
 * Top-level app shell. Holds the selected date and swaps between the day screen
 * and the widget-settings screen (раздел 6.4 / 6.5) without a nav library.
 */
@Composable
fun App(
    initialYear: Int,
    initialMonth: Int,
    initialDay: Int,
    calendarPermissionGranted: Boolean,
    onRequestCalendarPermission: () -> Unit,
) {
    var screen by remember { mutableStateOf(Screen.Day) }
    var year by remember { mutableIntStateOf(initialYear) }
    var month by remember { mutableIntStateOf(initialMonth) }
    var day by remember { mutableIntStateOf(initialDay) }

    val bg = Brush.verticalGradient(listOf(AppBgTop, AppBgBottom))

    androidx.compose.foundation.layout.Box(
        modifier = Modifier.fillMaxSize().background(bg),
    ) {
        CompositionLocalProvider(
            LocalContentColor provides Color.White,
            LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = Manrope),
        ) {
        when (screen) {
            Screen.Day -> DayScreen(
                year = year,
                month = month,
                day = day,
                permissionGranted = calendarPermissionGranted,
                onRequestPermission = onRequestCalendarPermission,
                onSelect = { y, m, d -> year = y; month = m; day = d },
                onChangeMonth = { delta ->
                    val (ny, nm) = CalendarMath.addMonths(year, month, delta)
                    year = ny; month = nm
                    day = day.coerceAtMost(CalendarMath.daysInMonth(ny, nm))
                },
                onOpenSettings = { screen = Screen.Settings },
            )

            Screen.Settings -> WidgetSettingsScreen(
                permissionGranted = calendarPermissionGranted,
                onRequestPermission = onRequestCalendarPermission,
                onBack = { screen = Screen.Day },
            )
        }
        }
    }
}

/** Shared transparent scrim colour used by card surfaces in both screens. */
internal val CardSurface = Color.White.copy(alpha = 0.045f)
internal val CardOutline = Color.White.copy(alpha = 0.07f)
