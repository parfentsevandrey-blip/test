package app.quire.calendar

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.CalendarContract

/**
 * Stands in for the system calendar provider.
 *
 * Rows are described by column name and materialised in whatever order the
 * caller asked for, so EventRepository's hand-written column indices are checked
 * against its own projection rather than against a copy of it.
 */
class FakeCalendarProvider : ContentProvider() {

    override fun onCreate() = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val columns = projection ?: emptyArray()
        val cursor = MatrixCursor(columns)
        var source = if (uri.toString().startsWith(CalendarContract.Calendars.CONTENT_URI.toString())) {
            calendars
        } else {
            instances
        }
        // The real provider hands instances back in start order; colour ordering
        // downstream depends on it, so honour the sort the caller asked for.
        if (sortOrder?.contains(CalendarContract.Instances.BEGIN) == true) {
            source = source.sortedBy { it[CalendarContract.Instances.BEGIN] as? Long ?: 0L }
        }
        source.forEach { row -> cursor.addRow(columns.map { row[it] }) }
        return cursor
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0

    companion object {
        var instances: List<Map<String, Any?>> = emptyList()
        var calendars: List<Map<String, Any?>> = emptyList()

        fun reset() {
            instances = emptyList()
            calendars = emptyList()
        }

        fun instance(
            eventId: Long,
            beginMillis: Long,
            endMillis: Long,
            startDay: Long,
            endDay: Long,
            title: String,
            allDay: Int = 0,
            location: String? = null,
            colour: Int? = 0xFF2E4A7D.toInt(),
            calendarId: Long = 1L,
            calendarName: String = "Personal",
            status: Int? = CalendarContract.Events.STATUS_CONFIRMED,
            selfStatus: Int? = CalendarContract.Attendees.ATTENDEE_STATUS_ACCEPTED,
        ): Map<String, Any?> = mapOf(
            CalendarContract.Instances.EVENT_ID to eventId,
            CalendarContract.Instances.BEGIN to beginMillis,
            CalendarContract.Instances.END to endMillis,
            CalendarContract.Instances.START_DAY to startDay,
            CalendarContract.Instances.END_DAY to endDay,
            CalendarContract.Events.TITLE to title,
            CalendarContract.Events.ALL_DAY to allDay,
            CalendarContract.Events.EVENT_LOCATION to location,
            CalendarContract.Events.DISPLAY_COLOR to colour,
            CalendarContract.Events.CALENDAR_ID to calendarId,
            CalendarContract.Events.CALENDAR_DISPLAY_NAME to calendarName,
            CalendarContract.Events.STATUS to status,
            CalendarContract.Events.SELF_ATTENDEE_STATUS to selfStatus,
        )

        fun calendar(id: Long, name: String, account: String, colour: Int): Map<String, Any?> =
            mapOf(
                CalendarContract.Calendars._ID to id,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME to name,
                CalendarContract.Calendars.ACCOUNT_NAME to account,
                CalendarContract.Calendars.CALENDAR_COLOR to colour,
            )
    }
}
