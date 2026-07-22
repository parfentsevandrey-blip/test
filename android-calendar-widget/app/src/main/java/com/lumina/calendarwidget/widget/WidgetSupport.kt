package com.lumina.calendarwidget.widget

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import androidx.glance.text.FontWeight
import androidx.glance.unit.ColorProvider
import com.lumina.calendarwidget.R
import com.lumina.calendarwidget.data.FontWeightOption
import com.lumina.calendarwidget.data.GradientOption
import com.lumina.calendarwidget.data.HeaderFormat
import com.lumina.calendarwidget.data.HeaderWeight
import com.lumina.calendarwidget.data.LabelCase
import com.lumina.calendarwidget.data.ThemeColors
import com.lumina.calendarwidget.data.WeekdayLabelFormat
import java.time.DayOfWeek
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/* ---- Color plumbing -------------------------------------------------------------------- */

fun Long.toColor(): Color = Color(this)
fun Long.provider(): ColorProvider = ColorProvider(Color(this))

fun Context.isSystemDark(): Boolean =
    (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

/* ---- Dynamic (Material You) color ------------------------------------------------------ */

/**
 * Build a palette from the wallpaper-derived system colors (Android 12+). Returns null on older
 * devices so the caller falls back to the selected preset.
 */
fun dynamicThemeColors(context: Context, isDark: Boolean): ThemeColors? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    fun c(resId: Int): Long = (context.getColor(resId).toLong() and 0xFFFFFFFF)
    return try {
        if (isDark) {
            ThemeColors(
                id = "dynamic", name = "Dynamic", isDark = true,
                background = c(android.R.color.system_neutral1_900),
                backgroundGradientEnd = c(android.R.color.system_neutral1_900),
                surface = c(android.R.color.system_neutral1_800),
                onSurface = c(android.R.color.system_neutral1_100),
                muted = c(android.R.color.system_neutral2_400),
                accent = c(android.R.color.system_accent1_200),
                onAccent = c(android.R.color.system_accent1_800),
                todayBg = c(android.R.color.system_accent1_200),
                todayText = c(android.R.color.system_accent1_800),
                weekendText = c(android.R.color.system_accent3_200),
                gridLine = c(android.R.color.system_neutral2_700),
                headerText = c(android.R.color.system_neutral1_50),
            )
        } else {
            ThemeColors(
                id = "dynamic", name = "Dynamic", isDark = false,
                background = c(android.R.color.system_neutral1_100),
                backgroundGradientEnd = c(android.R.color.system_neutral1_100),
                surface = c(android.R.color.system_neutral1_10),
                onSurface = c(android.R.color.system_neutral1_900),
                muted = c(android.R.color.system_neutral2_500),
                accent = c(android.R.color.system_accent1_600),
                onAccent = c(android.R.color.system_accent1_0),
                todayBg = c(android.R.color.system_accent1_600),
                todayText = c(android.R.color.system_accent1_0),
                weekendText = c(android.R.color.system_accent3_600),
                gridLine = c(android.R.color.system_neutral2_100),
                headerText = c(android.R.color.system_neutral1_900),
            )
        }
    } catch (_: Exception) {
        null
    }
}

/* ---- Gradient backgrounds -------------------------------------------------------------- */

@DrawableRes
fun gradientDrawable(option: GradientOption): Int? = when (option) {
    GradientOption.NONE -> null
    GradientOption.SUNRISE -> R.drawable.widget_bg_sunrise
    GradientOption.OCEAN -> R.drawable.widget_bg_ocean
    GradientOption.FOREST -> R.drawable.widget_bg_forest
    GradientOption.TWILIGHT -> R.drawable.widget_bg_twilight
    GradientOption.SLATE -> R.drawable.widget_bg_slate
    GradientOption.OBSIDIAN -> R.drawable.widget_bg_obsidian
}

/* ---- Typography helpers ---------------------------------------------------------------- */

fun FontWeightOption.toGlance(): FontWeight = when (this) {
    FontWeightOption.NORMAL -> FontWeight.Normal
    FontWeightOption.MEDIUM -> FontWeight.Medium
    FontWeightOption.BOLD -> FontWeight.Bold
}

fun HeaderWeight.toGlance(): FontWeight = when (this) {
    HeaderWeight.MEDIUM -> FontWeight.Medium
    HeaderWeight.BOLD -> FontWeight.Bold
}

/* ---- Header + weekday text ------------------------------------------------------------- */

/** The masthead split into a month part and an optional year part (for two-tone rendering). */
data class HeaderText(val month: String, val year: String?)

fun headerText(
    format: HeaderFormat,
    yearMonth: YearMonth,
    locale: Locale,
    dropYear: Boolean,
): HeaderText {
    // STANDALONE gives the nominative month name (e.g. Russian "Июль", not the genitive "Июля").
    val full = yearMonth.month.getDisplayName(TextStyle.FULL_STANDALONE, locale).replaceFirstChar { it.uppercase(locale) }
    val short = yearMonth.month.getDisplayName(TextStyle.SHORT_STANDALONE, locale).replaceFirstChar { it.uppercase(locale) }
    val year = yearMonth.year.toString()
    return when (format) {
        HeaderFormat.MONTH_YEAR -> HeaderText(full, if (dropYear) null else year)
        HeaderFormat.MON_YEAR -> HeaderText(short, if (dropYear) null else year)
        HeaderFormat.MONTH_ONLY -> HeaderText(full, null)
        HeaderFormat.ISO -> HeaderText("%04d-%02d".format(yearMonth.year, yearMonth.monthValue), null)
        HeaderFormat.HIDDEN -> HeaderText("", null)
    }
}

fun weekdayLabel(
    day: DayOfWeek,
    format: WeekdayLabelFormat,
    case: LabelCase,
    locale: Locale,
): String {
    val raw = when (format) {
        WeekdayLabelFormat.SINGLE -> day.getDisplayName(TextStyle.NARROW, locale)
        WeekdayLabelFormat.TWO -> day.getDisplayName(TextStyle.SHORT, locale).take(2)
        WeekdayLabelFormat.SHORT -> day.getDisplayName(TextStyle.SHORT, locale)
        WeekdayLabelFormat.HIDDEN -> ""
    }
    return when (case) {
        LabelCase.UPPER -> raw.uppercase(locale)
        LabelCase.TITLE -> raw.replaceFirstChar { it.uppercase(locale) }
        LabelCase.LOWER -> raw.lowercase(locale)
    }
}
