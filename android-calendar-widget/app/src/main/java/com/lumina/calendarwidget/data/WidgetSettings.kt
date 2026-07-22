package com.lumina.calendarwidget.data

import java.time.DayOfWeek
import java.time.temporal.WeekFields
import java.util.Locale

/*
 * The full, persisted configuration for the widget. Every field maps to a user-facing control in
 * the customization screen. Enums carry a human [label] for the UI and are persisted by their
 * stable Kotlin `name`, so reordering the display list never corrupts saved values.
 */

/** How the light/dark look is chosen. */
enum class ThemeMode(val label: String) {
    SYSTEM("Follow system"),
    LIGHT("Always light"),
    DARK("Always dark"),
    AMOLED("AMOLED black");
}

enum class CalendarViewMode(val label: String) {
    MONTH("Month grid"),
    TWO_WEEKS("Two weeks"),
    WEEK("Single week"),
    AGENDA("Agenda list");
}

enum class Density(val label: String, val scale: Float) {
    COMPACT("Compact", 0.86f),
    COMFORTABLE("Comfortable", 1.0f),
    SPACIOUS("Spacious", 1.16f);
}

enum class HeaderFormat(val label: String) {
    MONTH_YEAR("July 2026"),
    MON_YEAR("Jul 2026"),
    MONTH_ONLY("July"),
    ISO("2026-07"),
    HIDDEN("Hidden");
}

enum class HeaderAlignment(val label: String) {
    START("Left"),
    CENTER("Center"),
    END("Right");
}

enum class WeekdayLabelFormat(val label: String) {
    SINGLE("Single letter"),
    TWO("Two letters"),
    SHORT("Short (Mon)"),
    HIDDEN("Hidden");
}

enum class LabelCase(val label: String) {
    UPPER("UPPERCASE"),
    TITLE("Title"),
    LOWER("lowercase");
}

enum class HeaderWeight(val label: String) {
    MEDIUM("Medium"),
    BOLD("Bold");
}

enum class FontWeightOption(val label: String) {
    NORMAL("Normal"),
    MEDIUM("Medium"),
    BOLD("Bold");
}

enum class FirstDayOption(val label: String) {
    SYSTEM("Follow system"),
    MONDAY("Monday"),
    SUNDAY("Sunday"),
    SATURDAY("Saturday");

    fun resolve(locale: Locale): DayOfWeek = when (this) {
        SYSTEM -> WeekFields.of(locale).firstDayOfWeek
        MONDAY -> DayOfWeek.MONDAY
        SUNDAY -> DayOfWeek.SUNDAY
        SATURDAY -> DayOfWeek.SATURDAY
    }
}

enum class WeekendOption(val label: String, val days: Set<DayOfWeek>) {
    SAT_SUN("Sat & Sun", setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)),
    FRI_SAT("Fri & Sat", setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)),
    FRI("Friday only", setOf(DayOfWeek.FRIDAY)),
    SUN("Sunday only", setOf(DayOfWeek.SUNDAY)),
    NONE("None", emptySet());
}

enum class TodayStyle(val label: String) {
    ROUNDED_SQUARE("Rounded square"),
    FILLED_CIRCLE("Filled circle"),
    INLAID_JEWEL("Inlaid jewel"),
    OUTLINE_RING("Outline ring"),
    BOLD_NUMBER("Bold number"),
    NONE("None");
}

enum class SelectedStyle(val label: String) {
    FILLED("Filled"),
    OUTLINE_RING("Outline ring"),
    NONE("None");
}

enum class EventIndicatorStyle(val label: String) {
    DOT("Dot"),
    BAR("Bar"),
    COUNT("Count badge");
}

enum class TapDayAction(val label: String) {
    OPEN_CALENDAR("Open calendar app"),
    OPEN_APP("Open Lumina"),
    SELECT("Select the date"),
    NOTHING("Do nothing");
}

enum class TapHeaderAction(val label: String) {
    JUMP_TODAY("Jump to today"),
    OPEN_APP("Open Lumina"),
    NOTHING("Do nothing");
}

enum class LongPressAction(val label: String) {
    OPEN_SETTINGS("Open settings"),
    OPEN_APP("Open Lumina"),
    NOTHING("Do nothing");
}

enum class TimeFormat(val label: String) {
    SYSTEM("Follow system"),
    H12("12-hour"),
    H24("24-hour");
}

/** Preset background gradients, each backed by a `widget_bg_*` drawable. NONE means a flat fill. */
enum class GradientOption(val label: String) {
    NONE("None"),
    SUNRISE("Sunrise"),
    OCEAN("Ocean"),
    FOREST("Forest"),
    TWILIGHT("Twilight"),
    SLATE("Slate"),
    OBSIDIAN("Obsidian");
}

/**
 * The complete widget configuration. Defaults are chosen to look great out of the box: the
 * "Paper" light / "Ink" dark preset pair, month grid, comfortable density, rounded-square today.
 */
data class WidgetSettings(
    // Theme & color
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val lightPresetId: String = "paper",
    val darkPresetId: String = "ink",
    val dynamicColor: Boolean = false,
    val useCustomColors: Boolean = false,
    val accentColor: Long = 0xFFC4402C,
    val backgroundColor: Long = 0xFFEDEAE1,
    val surfaceColor: Long = 0xFFF7F5EF,
    val dayTextColor: Long = 0xFF1C1B18,
    val mutedTextColor: Long = 0xFFA9A398,
    val weekendTextColor: Long = 0xFFB0684F,
    val todayColor: Long = 0xFFC4402C,
    val backgroundOpacity: Int = 100,            // 0..100
    val gradient: GradientOption = GradientOption.NONE,
    val cornerRadiusDp: Int = 24,                 // 0..48

    // Layout
    val viewMode: CalendarViewMode = CalendarViewMode.MONTH,
    val density: Density = Density.COMFORTABLE,
    val headerFormat: HeaderFormat = HeaderFormat.MONTH_YEAR,
    val headerAlignment: HeaderAlignment = HeaderAlignment.START,
    val showHairline: Boolean = true,
    val showGridLines: Boolean = false,
    val showOtherMonthDays: Boolean = true,
    val lockSixRows: Boolean = true,

    // Typography
    val weekdayLabelFormat: WeekdayLabelFormat = WeekdayLabelFormat.SINGLE,
    val weekdayLabelCase: LabelCase = LabelCase.UPPER,
    val fontScalePercent: Int = 100,              // 80..140
    val headerWeight: HeaderWeight = HeaderWeight.MEDIUM,
    val dayNumberWeight: FontWeightOption = FontWeightOption.NORMAL,

    // Calendar
    val firstDayOfWeek: FirstDayOption = FirstDayOption.SYSTEM,
    val showWeekNumbers: Boolean = false,
    val weekend: WeekendOption = WeekendOption.SAT_SUN,
    val highlightWeekends: Boolean = true,
    val todayStyle: TodayStyle = TodayStyle.ROUNDED_SQUARE,
    val selectedStyle: SelectedStyle = SelectedStyle.OUTLINE_RING,
    val showEventIndicators: Boolean = true,
    val eventIndicatorStyle: EventIndicatorStyle = EventIndicatorStyle.DOT,

    // Behavior
    val tapDayAction: TapDayAction = TapDayAction.OPEN_CALENDAR,
    val tapHeaderAction: TapHeaderAction = TapHeaderAction.JUMP_TODAY,
    val longPressAction: LongPressAction = LongPressAction.OPEN_SETTINGS,

    // Advanced
    val timeFormat: TimeFormat = TimeFormat.SYSTEM,
    val localeTag: String = "system",

    // Runtime state (not a control; persisted so the "selected date" marker survives re-render)
    val selectedEpochDay: Long = -1L,             // -1 == no selection
) {
    /** Combined multiplier applied to all widget text: user percentage × density scale. */
    val fontScale: Float get() = fontScalePercent / 100f * density.scale

    fun resolvedLocale(): Locale =
        if (localeTag == "system") Locale.getDefault() else Locale.forLanguageTag(localeTag)
}
