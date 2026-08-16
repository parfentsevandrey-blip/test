package com.cozyhome.weather.util

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

private val ru = Locale.forLanguageTag("ru")
private val hourFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun formatTemp(value: Double): String {
    val rounded = value.roundToInt()
    val sign = if (rounded > 0) "+" else ""
    return "$sign$rounded°"
}

fun formatHour(isoTime: String): String =
    runCatching { LocalDateTime.parse(isoTime).format(hourFormatter) }.getOrDefault(isoTime.takeLast(5))

fun parseHour(isoTime: String): LocalDateTime? = runCatching { LocalDateTime.parse(isoTime) }.getOrNull()

fun formatDayOfWeek(isoDate: String, index: Int): String {
    if (index == 0) return "Сегодня"
    return runCatching {
        LocalDate.parse(isoDate).dayOfWeek.getDisplayName(TextStyle.SHORT_STANDALONE, ru)
            .replaceFirstChar { it.uppercase(ru) }
    }.getOrDefault(isoDate)
}

fun formatUpdatedAt(epochMs: Long): String {
    val time = java.time.Instant.ofEpochMilli(epochMs)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalTime()
    return time.format(hourFormatter)
}

/** Wind m/s → readable string. */
fun formatWind(kmh: Double): String {
    val ms = kmh / 3.6
    return "${(ms * 10).roundToInt() / 10.0} м/с"
}

/** hPa → mmHg. */
fun formatPressure(hpa: Double): String = "${(hpa * 0.750062).roundToInt()} мм"
