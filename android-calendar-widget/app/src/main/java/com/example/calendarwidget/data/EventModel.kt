package com.example.calendarwidget.data

import androidx.annotation.ColorInt

/**
 * Event categories from the design handoff (раздел 7 — Design Tokens).
 * `WORK` deliberately has no fixed colour: it is rendered with the user's accent.
 */
enum class EventCategory(val title: String, @ColorInt val fallbackColor: Int) {
    WORK("Работа", 0xFF7C9CFF.toInt()),       // = accent (overridden at render time)
    PERSONAL("Личное", 0xFFC9A6FF.toInt()),
    HEALTH("Здоровье", 0xFFFF8A6B.toInt()),
    SOCIAL("Друзья", 0xFF54E6C0.toInt());

    companion object {
        /** Deterministically bucket an event onto one of the four categories. */
        fun fromCalendarId(calendarId: Long): EventCategory {
            val idx = (((calendarId % 4) + 4) % 4).toInt()
            return entries[idx]
        }
    }
}

/**
 * A single occurrence of a calendar event (an "instance" in CalendarContract terms).
 *
 * @param color the resolved colour used for the dot/agenda marker. Falls back to the
 *   category colour when the system calendar does not supply one.
 */
data class CalendarEvent(
    val id: Long,
    val title: String,
    val start: Long,
    val end: Long,
    val allDay: Boolean,
    @ColorInt val color: Int,
    val category: EventCategory,
)
