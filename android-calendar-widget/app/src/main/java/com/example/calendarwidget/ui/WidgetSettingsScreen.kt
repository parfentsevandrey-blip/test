package com.example.calendarwidget.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.calendarwidget.data.ColorUtils
import com.example.calendarwidget.data.SettingsRepository
import com.example.calendarwidget.data.WidgetSettings
import com.example.calendarwidget.widget.MonthWidget
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun WidgetSettingsScreen(
    permissionGranted: Boolean,
    onRequestPermission: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val repo = remember(context) { SettingsRepository(context) }
    val settings by repo.settings.collectAsState(initial = WidgetSettings())
    val scope = rememberCoroutineScope()
    val accent = ColorUtils.composeColor(settings.accent)
    val textMuted = Color.White.copy(alpha = 0.42f)

    fun apply(transform: (WidgetSettings) -> WidgetSettings) {
        scope.launch {
            repo.update(transform)
            MonthWidget().updateAll(context)
        }
    }

    val topInset = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = topInset + 8.dp, start = 18.dp, end = 18.dp, bottom = 32.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.06f))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Text("‹", color = Color.White.copy(alpha = 0.7f), fontSize = 18.sp) }
            Spacer(Modifier.width(14.dp))
            Text("Настройки", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        }

        Spacer(Modifier.height(18.dp))
        WidgetPreviewCard(settings)

        Spacer(Modifier.height(18.dp))
        SettingCard("Акцентный цвет") {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                WidgetSettings.ACCENTS.forEach { hex ->
                    val selected = hex.equals(settings.accent, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(ColorUtils.composeColor(hex))
                            .then(
                                if (selected) Modifier.border(3.dp, Color.White, CircleShape) else Modifier,
                            )
                            .clickable { apply { it.copy(accent = hex) } },
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        SettingCard("Прозрачность фона") {
            SliderRow(
                value = settings.bgOpacity,
                range = 0.15f..0.9f,
                steps = ((0.9f - 0.15f) / 0.05f).roundToInt() - 1,
                accent = accent,
                valueLabel = "${(settings.bgOpacity * 100).roundToInt()}%",
                onChange = { v -> apply { it.copy(bgOpacity = snap(v, 0.05f)) } },
            )
        }

        Spacer(Modifier.height(14.dp))
        SettingCard("Скругление углов") {
            SliderRow(
                value = settings.radius.toFloat(),
                range = 12f..40f,
                steps = ((40 - 12) / 2) - 1,
                accent = accent,
                valueLabel = "${settings.radius} px",
                onChange = { v -> apply { it.copy(radius = (snap(v, 2f)).roundToInt()) } },
            )
        }

        Spacer(Modifier.height(14.dp))
        SettingCard("Первый день недели") {
            Segmented(
                options = listOf("Понедельник", "Воскресенье"),
                selectedIndex = if (settings.firstDayMonday) 0 else 1,
                accent = accent,
                onSelect = { idx -> apply { it.copy(firstDayMonday = idx == 0) } },
            )
        }

        Spacer(Modifier.height(14.dp))
        SettingCard("Список событий") {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Показывать дела дня под сеткой",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = settings.showAgenda,
                    onCheckedChange = { checked -> apply { it.copy(showAgenda = checked) } },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = accent,
                        checkedThumbColor = Color.White,
                    ),
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        SettingCard("Размер шрифта") {
            val idx = WidgetSettings.FONT_SCALES.indexOfFirst { kotlin.math.abs(it - settings.fontScale) < 0.001f }
                .coerceAtLeast(0)
            Segmented(
                options = listOf("A", "A", "A"),
                selectedIndex = idx,
                accent = accent,
                labelSizes = listOf(13.sp, 16.sp, 20.sp),
                onSelect = { i -> apply { it.copy(fontScale = WidgetSettings.FONT_SCALES[i]) } },
            )
        }

        Spacer(Modifier.height(14.dp))
        SettingCard("Тема превью") {
            Segmented(
                options = listOf("Светлая", "Тёмная"),
                selectedIndex = if (settings.previewDark) 1 else 0,
                accent = accent,
                onSelect = { idx -> apply { it.copy(previewDark = idx == 1) } },
            )
        }

        if (!permissionGranted) {
            Spacer(Modifier.height(14.dp))
            SettingCard("Доступ к календарю") {
                Column {
                    Text(
                        "Без разрешения виджет показывает сетку без событий.",
                        color = textMuted,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(accent)
                            .clickable(onClick = onRequestPermission)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) { Text("Разрешить доступ", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun SettingCard(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CardSurface)
            .border(BorderStroke(1.dp, CardOutline), RoundedCornerShape(20.dp))
            .padding(16.dp),
    ) {
        Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        content()
    }
}

@Composable
private fun SliderRow(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    accent: Color,
    valueLabel: String,
    onChange: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            steps = steps.coerceAtLeast(0),
            modifier = Modifier.weight(1f),
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
            ),
        )
        Spacer(Modifier.width(12.dp))
        Text(valueLabel, color = accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Segmented(
    options: List<String>,
    selectedIndex: Int,
    accent: Color,
    labelSizes: List<androidx.compose.ui.unit.TextUnit>? = null,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val selected = i == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (selected) accent else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (selected) Color(ColorUtils.contrastOn(accent.toArgb())) else Color.White.copy(alpha = 0.7f),
                    fontSize = labelSizes?.getOrNull(i) ?: 14.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

// Quantises a slider value to a [step] grid (matches the handoff's discrete steps).
private fun snap(value: Float, step: Float): Float = (Math.round(value / step) * step)
