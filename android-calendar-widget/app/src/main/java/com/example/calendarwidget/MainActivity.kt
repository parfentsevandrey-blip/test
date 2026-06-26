package com.example.calendarwidget

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.example.calendarwidget.data.CalendarMath
import com.example.calendarwidget.ui.App
import com.example.calendarwidget.ui.theme.CalendarWidgetTheme

/**
 * Single-activity host for the Compose app (экран дня + настройки виджета).
 * Also the launch target for taps on a widget day cell, carrying the tapped date.
 */
class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_YEAR = "extra_year"
        const val EXTRA_MONTH = "extra_month"
        const val EXTRA_DAY = "extra_day"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val today = CalendarMath.today()
        val initialYear = intent.getIntExtra(EXTRA_YEAR, today.year)
        val initialMonth = intent.getIntExtra(EXTRA_MONTH, today.month)
        val initialDay = intent.getIntExtra(EXTRA_DAY, today.day)

        setContent {
            var granted by remember { mutableStateOf(hasCalendarPermission()) }
            val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { result -> granted = result }

            // Ask for READ_CALENDAR on first launch (graceful — see CalendarRepository).
            androidx.compose.runtime.LaunchedEffect(Unit) {
                if (!granted) permissionLauncher.launch(Manifest.permission.READ_CALENDAR)
            }

            CalendarWidgetTheme {
                App(
                    initialYear = initialYear,
                    initialMonth = initialMonth,
                    initialDay = initialDay,
                    calendarPermissionGranted = granted,
                    onRequestCalendarPermission = {
                        permissionLauncher.launch(Manifest.permission.READ_CALENDAR)
                    },
                )
            }
        }
    }

    private fun hasCalendarPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED
}
