package app.quire.calendar

import android.Manifest
import android.app.Application
import android.content.Context
import android.provider.CalendarContract
import androidx.test.core.app.ApplicationProvider
import app.quire.calendar.core.EventRepository
import app.quire.calendar.core.MonthModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EventRepositoryTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val zone: ZoneId = ZoneId.systemDefault()
    private val day = LocalDate.of(2026, 8, 12)

    private fun millis(date: LocalDate, hour: Int) =
        date.atTime(LocalTime.of(hour, 0)).atZone(zone).toInstant().toEpochMilli()

    @Before
    fun setUp() {
        FakeCalendarProvider.reset()
        Robolectric.setupContentProvider(
            FakeCalendarProvider::class.java,
            CalendarContract.AUTHORITY,
        )
        shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .grantPermissions(Manifest.permission.READ_CALENDAR)
    }

    private fun standardRows() = listOf(
        FakeCalendarProvider.instance(
            eventId = 1,
            beginMillis = millis(day, 10),
            endMillis = millis(day, 11),
            startDay = MonthModel.julianDay(day),
            endDay = MonthModel.julianDay(day),
            title = "Board review",
            location = "Room 2",
            colour = 0xFF2E4A7D.toInt(),
        ),
        FakeCalendarProvider.instance(
            eventId = 2,
            beginMillis = millis(day, 0),
            endMillis = millis(day.plusDays(1), 0),
            startDay = MonthModel.julianDay(day),
            endDay = MonthModel.julianDay(day),
            title = "Offsite",
            allDay = 1,
            colour = 0xFF4C5D3C.toInt(),
        ),
        FakeCalendarProvider.instance(
            eventId = 3,
            beginMillis = millis(day, 9),
            endMillis = millis(day.plusDays(2), 18),
            startDay = MonthModel.julianDay(day),
            endDay = MonthModel.julianDay(day.plusDays(2)),
            title = "Sprint",
            colour = 0xFF9A6F21.toInt(),
        ),
        FakeCalendarProvider.instance(
            eventId = 4,
            beginMillis = millis(day, 14),
            endMillis = millis(day, 15),
            startDay = MonthModel.julianDay(day),
            endDay = MonthModel.julianDay(day),
            title = "Declined lunch",
            selfStatus = CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED,
        ),
        FakeCalendarProvider.instance(
            eventId = 5,
            beginMillis = millis(day, 16),
            endMillis = millis(day, 17),
            startDay = MonthModel.julianDay(day),
            endDay = MonthModel.julianDay(day),
            title = "Cancelled sync",
            status = CalendarContract.Events.STATUS_CANCELED,
        ),
        FakeCalendarProvider.instance(
            eventId = 6,
            beginMillis = millis(day, 18),
            endMillis = millis(day, 19),
            startDay = MonthModel.julianDay(day),
            endDay = MonthModel.julianDay(day),
            title = "Work standup",
            calendarId = 9L,
            calendarName = "Work",
        ),
    )

    @Test
    fun `agenda reads every column off the right index`() {
        FakeCalendarProvider.instances = standardRows()
        val agenda = EventRepository.agendaFor(context, day)

        // All-day first, then by start time: Sprint opens at 09:00, the board at 10:00.
        assertEquals(
            listOf("Offsite", "Sprint", "Board review", "Work standup"),
            agenda.map { it.title },
        )

        val allDay = agenda.first()
        assertTrue("all-day flag", allDay.allDay)
        assertEquals(2L, allDay.eventId)
        assertEquals(0xFF4C5D3C.toInt(), allDay.colour)

        val timed = agenda.first { it.title == "Board review" }
        assertFalse(timed.allDay)
        assertEquals(1L, timed.eventId)
        assertEquals(millis(day, 10), timed.begin)
        assertEquals(millis(day, 11), timed.end)
        assertEquals("Room 2", timed.location)
        assertEquals(0xFF2E4A7D.toInt(), timed.colour)
        assertEquals("Personal", timed.calendarName)
        assertNull(agenda.first { it.title == "Sprint" }.location)
    }

    @Test
    fun `declined and cancelled entries never reach the day`() {
        FakeCalendarProvider.instances = standardRows()
        val titles = EventRepository.agendaFor(context, day).map { it.title }
        assertFalse(titles.contains("Declined lunch"))
        assertFalse(titles.contains("Cancelled sync"))
    }

    @Test
    fun `hidden calendars drop out of both views`() {
        FakeCalendarProvider.instances = standardRows()
        val hidden = setOf(9L)
        assertFalse(
            EventRepository.agendaFor(context, day, hidden).map { it.title }.contains("Work standup"),
        )
        val loads = EventRepository.loadFor(context, day, 1, hidden)
        assertEquals(3, loads[day]?.count)
    }

    @Test
    fun `multi-day events count on every day they cover`() {
        FakeCalendarProvider.instances = standardRows()
        val loads = EventRepository.loadFor(context, day, 5)
        assertEquals(4, loads[day]?.count)
        assertEquals(1, loads[day.plusDays(1)]?.count)
        assertEquals(1, loads[day.plusDays(2)]?.count)
        assertNull(loads[day.plusDays(3)])
    }

    @Test
    fun `a day carries at most three distinct colours in start order`() {
        FakeCalendarProvider.instances = standardRows()
        val load = EventRepository.loadFor(context, day, 1)[day]!!
        assertEquals(3, load.colours.size)
        // Earliest first: the all-day Offsite starts at midnight.
        assertEquals(0xFF4C5D3C.toInt(), load.colours[0])
        assertEquals(0xFF9A6F21.toInt(), load.colours[1])
        assertEquals(0xFF2E4A7D.toInt(), load.colours[2])
        assertTrue(load.colours.toSet().size == load.colours.size)
    }

    @Test
    fun `an event spilling past the window is clipped to it`() {
        FakeCalendarProvider.instances = listOf(
            FakeCalendarProvider.instance(
                eventId = 10,
                beginMillis = millis(day.minusDays(10), 9),
                endMillis = millis(day.plusDays(400), 9),
                startDay = MonthModel.julianDay(day.minusDays(10)),
                endDay = MonthModel.julianDay(day.plusDays(400)),
                title = "Sabbatical",
            ),
        )
        val loads = EventRepository.loadFor(context, day, 3)
        assertEquals(3, loads.size)
        assertEquals(setOf(day, day.plusDays(1), day.plusDays(2)), loads.keys)
    }

    @Test
    fun `calendar sources come back with colour and account`() {
        FakeCalendarProvider.calendars = listOf(
            FakeCalendarProvider.calendar(1, "Personal", "me@example.com", 0xFF2E4A7D.toInt()),
            FakeCalendarProvider.calendar(9, "Work", "work@example.com", 0xFF9A6F21.toInt()),
        )
        val sources = EventRepository.calendars(context)
        assertEquals(2, sources.size)
        assertEquals("Personal", sources[0].displayName)
        assertEquals("work@example.com", sources[1].accountName)
        assertEquals(0xFF9A6F21.toInt(), sources[1].colour)
    }

    @Test
    fun `no permission means no reads and no crash`() {
        shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .denyPermissions(Manifest.permission.READ_CALENDAR)
        FakeCalendarProvider.instances = standardRows()
        assertTrue(EventRepository.agendaFor(context, day).isEmpty())
        assertTrue(EventRepository.loadFor(context, day, 7).isEmpty())
        assertTrue(EventRepository.calendars(context).isEmpty())
    }
}
