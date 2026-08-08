package app.quire.calendar.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import app.quire.calendar.core.DayLoad
import app.quire.calendar.core.EventRepository
import app.quire.calendar.core.MonthModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.util.concurrent.Executors

/**
 * Keeps a month's event marks one swipe ahead of the user. The pager asks for a
 * month, gets whatever is already cached immediately, and receives the rest on
 * the main thread when the provider answers.
 */
class MonthLoader(context: Context) {

    private val app = context.applicationContext
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "quire-events").apply { isDaemon = true }
    }
    private val main = Handler(Looper.getMainLooper())

    /** Access-ordered so the least recently drawn month is the one evicted. */
    private val cache = object : LinkedHashMap<Key, Map<LocalDate, DayLoad>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<Key, Map<LocalDate, DayLoad>>) = size > 12
    }

    private data class Key(val month: YearMonth, val firstDay: DayOfWeek, val stamp: Int)

    private var stamp = 0
    private val inFlight = HashSet<Key>()

    fun invalidate() {
        synchronized(cache) {
            cache.clear()
            inFlight.clear()
            stamp++
        }
    }

    fun cached(month: YearMonth, firstDay: DayOfWeek): Map<LocalDate, DayLoad>? =
        synchronized(cache) { cache[Key(month, firstDay, stamp)] }

    fun request(
        month: YearMonth,
        firstDay: DayOfWeek,
        hidden: Set<Long>,
        onReady: (YearMonth, Map<LocalDate, DayLoad>) -> Unit,
    ) {
        val key = synchronized(cache) {
            val k = Key(month, firstDay, stamp)
            cache[k]?.let { ready ->
                onReady(month, ready)
                return
            }
            if (!inFlight.add(k)) return
            k
        }
        executor.execute {
            val from = MonthModel.cells(month, firstDay).first()
            val loads = EventRepository.loadFor(app, from, MonthModel.CELLS, hidden)
            main.post {
                synchronized(cache) {
                    inFlight.remove(key)
                    if (key.stamp == stamp) cache[key] = loads
                }
                if (key.stamp == stamp) onReady(month, loads)
            }
        }
    }

    fun search(
        text: String,
        around: LocalDate,
        hidden: Set<Long>,
        onReady: (List<app.quire.calendar.core.AgendaEntry>) -> Unit,
    ) {
        executor.execute {
            val found = EventRepository.search(app, text, around, hidden = hidden)
            main.post { onReady(found) }
        }
    }

    fun agenda(
        date: LocalDate,
        hidden: Set<Long>,
        onReady: (LocalDate, List<app.quire.calendar.core.AgendaEntry>) -> Unit,
    ) {
        executor.execute {
            val entries = EventRepository.agendaFor(app, date, hidden)
            main.post { onReady(date, entries) }
        }
    }
}
