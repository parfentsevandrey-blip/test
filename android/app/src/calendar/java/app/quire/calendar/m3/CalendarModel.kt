package app.quire.calendar.m3

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.AndroidViewModel
import app.quire.calendar.core.AgendaEntry
import app.quire.calendar.core.CalendarSource
import app.quire.calendar.core.DayLoad
import app.quire.calendar.core.EventRepository
import app.quire.calendar.core.MonthLoader
import app.quire.calendar.core.MonthModel
import app.quire.calendar.core.Prefs
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

/**
 * Everything the screens read and everything they can ask for.
 *
 * The calendar itself is unchanged from before the redesign — `MonthModel` does the grid
 * arithmetic, `EventRepository` reads the provider and `MonthLoader` keeps a month's marks one
 * swipe ahead on a background thread. Only the interface on top of them was rebuilt, so the part
 * that had tests and had been debugged against a real provider was kept.
 *
 * State is Compose state rather than a flow: every one of these is read during composition and
 * nothing here outlives the screen, so the simplest thing that recomposes correctly is the right
 * one.
 */
class CalendarModel(app: Application) : AndroidViewModel(app) {

    private val prefs = Prefs.get(app)
    private val loader = MonthLoader(app)

    /** The month the pager is on, which is what the app bar names. */
    var month by mutableStateOf(YearMonth.now())
        private set

    /** The day whose agenda is shown under the grid. */
    var selected by mutableStateOf(LocalDate.now())
        private set

    /** Recomputed on resume so a calendar left open overnight does not lie about today. */
    var today by mutableStateOf(LocalDate.now())
        private set

    var firstDayOfWeek by mutableStateOf(
        MonthModel.firstDayOfWeek(prefs.firstDay, Locale.getDefault()),
    )
        private set

    var settings by mutableStateOf(Settings.from(prefs))
        private set

    var calendars by mutableStateOf<List<CalendarSource>>(emptyList())
        private set

    var hasPermission by mutableStateOf(EventRepository.hasPermission(app))
        private set

    /** Marks per month, filled in as the provider answers; a missing month simply has none yet. */
    val loads: SnapshotStateMap<YearMonth, Map<LocalDate, DayLoad>> = mutableStateMapOf()

    /** The selected day's entries, and whether they are still being fetched. */
    var agenda by mutableStateOf<List<AgendaEntry>>(emptyList())
        private set

    /**
     * True between asking the provider for a day and hearing back.
     *
     * Without it an empty list means two different things — "nothing on" and "not asked yet" —
     * and every day you open says "nothing scheduled" for a frame before its entries arrive.
     */
    var agendaLoading by mutableStateOf(false)
        private set

    var query by mutableStateOf("")
        private set

    var results by mutableStateOf<List<AgendaEntry>>(emptyList())
        private set

    private var searching = false

    /** Everything the settings screen edits, read from and written back to one place. */
    data class Settings(
        val dynamic: Boolean,
        val dark: Boolean?,
        val firstDay: String,
        val showAdjacent: Boolean,
        val dimWeekends: Boolean,
        val weekNumbers: Boolean,
        val colouredMarks: Boolean,
        val density: Boolean,
        val swipeNav: Boolean,
        val hidden: Set<Long>,
    ) {
        companion object {
            fun from(prefs: Prefs) = Settings(
                dynamic = prefs.dynamic,
                dark = when (prefs.skin) {
                    app.quire.calendar.core.Skin.PAPER -> false
                    app.quire.calendar.core.Skin.INK,
                    app.quire.calendar.core.Skin.COLOUR,
                    -> true
                    app.quire.calendar.core.Skin.AUTO -> null
                },
                firstDay = prefs.firstDay,
                showAdjacent = prefs.showAdjacent,
                dimWeekends = prefs.dimWeekends,
                weekNumbers = prefs.weekNumbers,
                colouredMarks = prefs.colouredDots,
                density = prefs.heat,
                swipeNav = prefs.swipeNav,
                hidden = prefs.hiddenCalendars,
            )
        }
    }

    /** True while a pull-to-refresh is being honoured, so the indicator has something to follow. */
    var refreshing by mutableStateOf(false)
        private set

    fun refresh() {
        today = LocalDate.now()
        hasPermission = EventRepository.hasPermission(getApplication())
        firstDayOfWeek = MonthModel.firstDayOfWeek(prefs.firstDay, Locale.getDefault())
        calendars = EventRepository.calendars(getApplication())
        loader.invalidate()
        loads.clear()
        refreshing = true
        // The month is what the indicator is over, so its arrival is what ends the refresh —
        // the agenda underneath carries its own.
        loader.request(month, firstDayOfWeek, settings.hidden) { answered, marks ->
            loads[answered] = marks
            refreshing = false
        }
        request(month.minusMonths(1))
        request(month.plusMonths(1))
        openDay(selected)
    }

    /** Asks for a month's marks, unless they are already in hand. */
    fun request(target: YearMonth) {
        if (loads.containsKey(target)) return
        loader.request(target, firstDayOfWeek, settings.hidden) { answered, marks ->
            loads[answered] = marks
        }
    }

    fun showMonth(target: YearMonth) {
        month = target
        request(target)
        // A month either side, so a swipe finds its marks already there rather than blank.
        request(target.minusMonths(1))
        request(target.plusMonths(1))
    }

    fun openDay(date: LocalDate) {
        selected = date
        if (month != YearMonth.from(date)) showMonth(YearMonth.from(date))
        agenda = emptyList()
        agendaLoading = true
        loader.agenda(date, settings.hidden) { answered, entries ->
            // A day opened while another was still being fetched: the stale answer is dropped, and
            // with it any claim about whether the day now showing is still loading.
            if (answered == selected) {
                agenda = entries
                agendaLoading = false
            }
        }
    }

    fun goToToday() {
        today = LocalDate.now()
        openDay(today)
    }

    fun search(text: String) {
        query = text
        if (text.trim().length < 2) {
            results = emptyList()
            return
        }
        if (searching) return
        searching = true
        loader.search(text, selected, settings.hidden) { found ->
            searching = false
            // The field may have moved on while the provider was answering.
            if (query.trim().length >= 2) results = found
        }
    }

    fun update(next: Settings) {
        prefs.dynamic = next.dynamic
        prefs.skin = when (next.dark) {
            null -> app.quire.calendar.core.Skin.AUTO
            false -> app.quire.calendar.core.Skin.PAPER
            true -> app.quire.calendar.core.Skin.INK
        }
        prefs.firstDay = next.firstDay
        prefs.showAdjacent = next.showAdjacent
        prefs.dimWeekends = next.dimWeekends
        prefs.weekNumbers = next.weekNumbers
        prefs.colouredDots = next.colouredMarks
        prefs.heat = next.density
        prefs.swipeNav = next.swipeNav
        prefs.hiddenCalendars = next.hidden
        settings = next
        firstDayOfWeek = MonthModel.firstDayOfWeek(next.firstDay, Locale.getDefault())
        loader.invalidate()
        loads.clear()
        request(month)
    }

    fun permissionGranted() {
        hasPermission = true
        refresh()
    }

    /** The six weeks of a month, as the grid draws them. */
    fun cells(target: YearMonth): List<LocalDate> = MonthModel.cells(target, firstDayOfWeek)

    fun weekdayLabels(): List<String> =
        MonthModel.weekdayLabels(firstDayOfWeek, Locale.getDefault())

    fun weekdayOrder(): List<DayOfWeek> = MonthModel.weekdayOrder(firstDayOfWeek)
}
