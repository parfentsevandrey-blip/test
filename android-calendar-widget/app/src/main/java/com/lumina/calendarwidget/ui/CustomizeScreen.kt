@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.lumina.calendarwidget.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lumina.calendarwidget.calendar.EventRepository
import com.lumina.calendarwidget.data.CalendarViewMode
import com.lumina.calendarwidget.data.Density
import com.lumina.calendarwidget.data.EventIndicatorStyle
import com.lumina.calendarwidget.data.FirstDayOption
import com.lumina.calendarwidget.data.FontWeightOption
import com.lumina.calendarwidget.data.GradientOption
import com.lumina.calendarwidget.data.HeaderAlignment
import com.lumina.calendarwidget.data.HeaderFormat
import com.lumina.calendarwidget.data.HeaderWeight
import com.lumina.calendarwidget.data.LabelCase
import com.lumina.calendarwidget.data.SelectedStyle
import com.lumina.calendarwidget.data.ThemeCatalog
import com.lumina.calendarwidget.data.ThemeMode
import com.lumina.calendarwidget.data.TapDayAction
import com.lumina.calendarwidget.data.TapHeaderAction
import com.lumina.calendarwidget.data.TimeFormat
import com.lumina.calendarwidget.data.TodayStyle
import com.lumina.calendarwidget.data.WeekdayLabelFormat
import com.lumina.calendarwidget.data.WeekendOption
import com.lumina.calendarwidget.data.WidgetSettings

private val COLOR_PALETTE = listOf(
    0xFFC4402C, 0xFFE9634A, 0xFF2B54D4, 0xFF5A8CE0, 0xFF3EA783, 0xFF6750A4,
    0xFFD0BCFF, 0xFFB0295A, 0xFFCBA75E, 0xFF8C6A2A, 0xFF1C1B18, 0xFF14161C,
    0xFFFFFFFF, 0xFFF7F5EF, 0xFFEDEAE1, 0xFF8A8F98,
)

private val LOCALES = listOf(
    "system" to "System",
    "en-US" to "English (US)",
    "en-GB" to "English (UK)",
    "de-DE" to "Deutsch",
    "fr-FR" to "Français",
    "es-ES" to "Español",
    "ru-RU" to "Русский",
    "ja-JP" to "日本語",
)

@Composable
fun CustomizeScreen(vm: CustomizeViewModel) {
    val s by vm.settings.collectAsState()
    val context = LocalContext.current

    var hasCalendarPermission by rememberSaveable { mutableStateOf(EventRepository.hasPermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCalendarPermission = granted
        if (granted) vm.refreshWidgets()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Lumina Calendar", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Customize your month widget",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { vm.resetToDefaults() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Reset to defaults")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(bottom = 32.dp),
        ) {
            PreviewStage(s)
            AddWidgetHint()

            SettingSection("Theme & color") {
                ChipRow("Theme mode", ThemeMode.entries, s.themeMode, { it.label }) { vm.edit { c -> c.copy(themeMode = it) } }
                SwitchRow(
                    "Material You dynamic color",
                    "Recolor from the wallpaper on Android 12+",
                    s.dynamicColor,
                ) { vm.edit { c -> c.copy(dynamicColor = it) } }
                SwitchRow("Use custom colors", "Override the preset with your own palette", s.useCustomColors) {
                    vm.edit { c -> c.copy(useCustomColors = it) }
                }
                if (s.useCustomColors) {
                    ColorRow("Accent", COLOR_PALETTE, s.accentColor) { vm.edit { c -> c.copy(accentColor = it, todayColor = it) } }
                    ColorRow("Surface", COLOR_PALETTE, s.surfaceColor) { vm.edit { c -> c.copy(surfaceColor = it) } }
                    ColorRow("Day numbers", COLOR_PALETTE, s.dayTextColor) { vm.edit { c -> c.copy(dayTextColor = it) } }
                    ColorRow("Muted text", COLOR_PALETTE, s.mutedTextColor) { vm.edit { c -> c.copy(mutedTextColor = it) } }
                    ColorRow("Weekend", COLOR_PALETTE, s.weekendTextColor) { vm.edit { c -> c.copy(weekendTextColor = it) } }
                } else {
                    ThemeGallery(s, vm)
                }
                ChipRow("Background gradient", GradientOption.entries, s.gradient, { it.label }) { vm.edit { c -> c.copy(gradient = it) } }
                SliderRow("Background opacity", s.backgroundOpacity, 0, 100, 5, "%") { vm.edit { c -> c.copy(backgroundOpacity = it) } }
                SliderRow("Corner radius", s.cornerRadiusDp, 0, 48, 2, "dp") { vm.edit { c -> c.copy(cornerRadiusDp = it) } }
            }

            SettingSection("Layout") {
                ChipRow("Calendar view", CalendarViewMode.entries, s.viewMode, { it.label }) { vm.edit { c -> c.copy(viewMode = it) } }
                ChipRow("Density", Density.entries, s.density, { it.label }) { vm.edit { c -> c.copy(density = it) } }
                ChipRow("Header format", HeaderFormat.entries, s.headerFormat, { it.label }) { vm.edit { c -> c.copy(headerFormat = it) } }
                ChipRow("Header alignment", HeaderAlignment.entries, s.headerAlignment, { it.label }) { vm.edit { c -> c.copy(headerAlignment = it) } }
                SwitchRow("Weekday hairline", "The single rule under the weekday initials", s.showHairline) { vm.edit { c -> c.copy(showHairline = it) } }
                SwitchRow("Full grid lines", "Ruled separators between weeks", s.showGridLines) { vm.edit { c -> c.copy(showGridLines = it) } }
                SwitchRow("Show other-month days", null, s.showOtherMonthDays) { vm.edit { c -> c.copy(showOtherMonthDays = it) } }
                SwitchRow("Fixed height (6 rows)", "Never jump between short and long months", s.lockSixRows) { vm.edit { c -> c.copy(lockSixRows = it) } }
            }

            SettingSection("Typography") {
                ChipRow("Weekday labels", WeekdayLabelFormat.entries, s.weekdayLabelFormat, { it.label }) { vm.edit { c -> c.copy(weekdayLabelFormat = it) } }
                ChipRow("Label case", LabelCase.entries, s.weekdayLabelCase, { it.label }) { vm.edit { c -> c.copy(weekdayLabelCase = it) } }
                ChipRow("Header weight", HeaderWeight.entries, s.headerWeight, { it.label }) { vm.edit { c -> c.copy(headerWeight = it) } }
                ChipRow("Day number weight", FontWeightOption.entries, s.dayNumberWeight, { it.label }) { vm.edit { c -> c.copy(dayNumberWeight = it) } }
                SliderRow("Text size", s.fontScalePercent, 80, 140, 5, "%") { vm.edit { c -> c.copy(fontScalePercent = it) } }
            }

            SettingSection("Calendar") {
                ChipRow("First day of week", FirstDayOption.entries, s.firstDayOfWeek, { it.label }) { vm.edit { c -> c.copy(firstDayOfWeek = it) } }
                SwitchRow("Week numbers", "ISO week number beside each row", s.showWeekNumbers) { vm.edit { c -> c.copy(showWeekNumbers = it) } }
                ChipRow("Weekend days", WeekendOption.entries, s.weekend, { it.label }) { vm.edit { c -> c.copy(weekend = it) } }
                SwitchRow("Highlight weekends", null, s.highlightWeekends) { vm.edit { c -> c.copy(highlightWeekends = it) } }
                ChipRow("Today highlight", TodayStyle.entries, s.todayStyle, { it.label }) { vm.edit { c -> c.copy(todayStyle = it) } }
                ChipRow("Selected date", SelectedStyle.entries, s.selectedStyle, { it.label }) { vm.edit { c -> c.copy(selectedStyle = it) } }
                SwitchRow("Event indicators", "Dots on days with events", s.showEventIndicators) { vm.edit { c -> c.copy(showEventIndicators = it) } }
                if (s.showEventIndicators) {
                    ChipRow("Indicator style", EventIndicatorStyle.entries, s.eventIndicatorStyle, { it.label }) { vm.edit { c -> c.copy(eventIndicatorStyle = it) } }
                    if (!hasCalendarPermission) {
                        CalendarPermissionCard { permissionLauncher.launch(Manifest.permission.READ_CALENDAR) }
                    }
                }
            }

            SettingSection("Behavior") {
                ChipRow("Tap a day", TapDayAction.entries, s.tapDayAction, { it.label }) { vm.edit { c -> c.copy(tapDayAction = it) } }
                ChipRow("Tap the header", TapHeaderAction.entries, s.tapHeaderAction, { it.label }) { vm.edit { c -> c.copy(tapHeaderAction = it) } }
            }

            SettingSection("Advanced") {
                ChipRow("Time format", TimeFormat.entries, s.timeFormat, { it.label }) { vm.edit { c -> c.copy(timeFormat = it) } }
                ChipRow("Locale", LOCALES.map { it.first }, s.localeTag, { tag -> LOCALES.first { it.first == tag }.second }) {
                    vm.edit { c -> c.copy(localeTag = it) }
                }
            }
        }
    }
}

@Composable
private fun PreviewStage(s: WidgetSettings) {
    // A soft "wallpaper" backdrop so background opacity and gradients read clearly.
    Box(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF6D83F2), Color(0xFF8E7BEF), Color(0xFFB56BE0))
                )
            )
            .padding(horizontal = 24.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        WidgetPreview(
            settings = s,
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
        )
    }
}

@Composable
private fun AddWidgetHint() {
    Text(
        "Long-press your home screen → Widgets → Lumina Calendar to place it. Every change here updates the widget instantly.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
    )
}

@Composable
private fun CalendarPermissionCard(onGrant: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            "Grant calendar access to show real event dots.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onGrant) { Text("Allow calendar access") }
    }
}

@Composable
private fun ThemeGallery(s: WidgetSettings, vm: CustomizeViewModel) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Light theme", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ThemeCatalog.lightPresets.forEach { theme ->
                PresetCard(theme, selected = s.lightPresetId == theme.id) {
                    vm.edit { it.copy(lightPresetId = theme.id) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Dark theme", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ThemeCatalog.darkPresets.forEach { theme ->
                PresetCard(theme, selected = s.darkPresetId == theme.id) {
                    vm.edit { it.copy(darkPresetId = theme.id) }
                }
            }
        }
    }
}
