package app.rosa.weather.widget.calendar

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.RectF
import android.provider.CalendarContract
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import app.rosa.weather.widget.R
import app.rosa.weather.widget.motion.setLiveWeather
import app.rosa.weather.widget.render.calendar.CalendarTargets
import app.rosa.weather.widget.render.calendar.CalendarView
import app.rosa.weather.widget.render.calendar.SeasonClock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

/**
 * Which month each calendar widget shows. Arrows move it a month at a time; tomorrow every
 * widget is back on the current month, as a paper calendar would be when you look at it again.
 */
internal class CalendarNavigation(context: Context) {
    private val prefs = context.getSharedPreferences("calendar_widgets", Context.MODE_PRIVATE)

    fun offset(widgetId: Int, today: LocalDate): Int =
        if (prefs.getLong(dayKey(widgetId), Long.MIN_VALUE) == today.toEpochDay()) prefs.getInt(offsetKey(widgetId), 0) else 0

    /** Moves by [delta] months, or back to this month when [delta] is 0. */
    fun move(widgetId: Int, delta: Int, today: LocalDate) {
        val next = if (delta == 0) 0 else (offset(widgetId, today) + delta).coerceIn(-240, 240)
        prefs.edit {
            putInt(offsetKey(widgetId), next)
            putLong(dayKey(widgetId), today.toEpochDay())
        }
    }

    fun forget(widgetIds: IntArray) = prefs.edit { widgetIds.forEach { remove(offsetKey(it)); remove(dayKey(it)) } }

    private fun offsetKey(id: Int) = "offset_$id"
    private fun dayKey(id: Int) = "day_$id"
}

/** The events on the phone's calendars, as the colours of each day's events. */
internal object CalendarEvents {
    fun granted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun read(context: Context, from: LocalDate, to: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Map<LocalDate, List<Int>> {
        if (!granted(context)) return emptyMap()
        val begin = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, begin)
            ContentUris.appendId(it, end)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.DISPLAY_COLOR,
        )
        val days = HashMap<LocalDate, MutableList<Int>>()
        runCatching {
            context.contentResolver.query(uri, projection, "${CalendarContract.Instances.VISIBLE} = 1", null, "${CalendarContract.Instances.BEGIN} ASC")?.use { cursor ->
                while (cursor.moveToNext()) {
                    val allDay = cursor.getInt(2) == 1
                    // All-day events are stored at midnight UTC; timed ones at their real instant.
                    val eventZone = if (allDay) ZoneOffset.UTC else zone
                    val first = Instant.ofEpochMilli(cursor.getLong(0)).atZone(eventZone).toLocalDate()
                    val endMillis = cursor.getLong(1) - if (allDay) 1 else 0
                    val last = Instant.ofEpochMilli(maxOf(endMillis, cursor.getLong(0))).atZone(eventZone).toLocalDate()
                    val color = cursor.getInt(3)
                    var day = maxOf(first, from)
                    var guard = 0
                    while (!day.isAfter(minOf(last, to)) && guard++ < 42) {
                        val list = days.getOrPut(day) { mutableListOf() }
                        if (color !in list) list += color
                        day = day.plusDays(1)
                    }
                }
            }
        }
        return days
    }
}

/**
 * Midnight: a new day to mark and every widget back on its current month. An alarm that may fire
 * in doze, a few minutes late at worst, without asking for exact alarms.
 */
internal object CalendarAlarm {
    fun scheduleMidnight(context: Context, provider: ComponentName) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val zone = ZoneId.systemDefault()
        val midnight = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() + 5_000
        val intent = Intent(CalendarIntents.ACTION_NEW_DAY).setComponent(provider)
        val pending = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        alarms.setAndAllowWhileIdle(AlarmManager.RTC, midnight, pending)
    }

    fun cancel(context: Context, provider: ComponentName) {
        val intent = Intent(CalendarIntents.ACTION_NEW_DAY).setComponent(provider)
        val pending = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE) ?: return
        context.getSystemService(AlarmManager::class.java)?.cancel(pending)
        pending.cancel()
    }
}

/** What each tap on a calendar widget does. */
internal object CalendarIntents {
    const val ACTION_MONTH = "app.rosa.weather.widget.action.CALENDAR_MONTH"
    const val ACTION_NEW_DAY = "app.rosa.weather.widget.action.CALENDAR_NEW_DAY"
    const val EXTRA_DELTA = "delta"

    private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    /** Whether a calendar app takes these intents: asked once, not for every day of every size. */
    @Volatile private var canView: Boolean? = null
    @Volatile private var canInsert: Boolean? = null

    fun month(context: Context, provider: ComponentName, widgetId: Int, delta: Int): PendingIntent {
        // A tap someone is waiting on: delivered and run at foreground priority, not queued behind
        // the background's broadcasts.
        val intent = Intent(ACTION_MONTH).setComponent(provider)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            .putExtra(EXTRA_DELTA, delta)
            .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        return PendingIntent.getBroadcast(context, widgetId * 4 + delta + 1, intent, FLAGS)
    }

    /** The phone's calendar on [date]; this app when there is no calendar to open. */
    fun day(context: Context, widgetId: Int, date: LocalDate): PendingIntent {
        val millis = date.atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val view = Intent(Intent.ACTION_VIEW)
            .setData(CalendarContract.CONTENT_URI.buildUpon().appendPath("time").appendPath(millis.toString()).build())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val ok = canView ?: (view.resolveActivity(context.packageManager) != null).also { canView = it }
        return PendingIntent.getActivity(context, widgetId, if (ok) view else app(context), FLAGS)
    }

    /** A new event in the phone's calendar, starting at the next full hour of [date]. */
    fun add(context: Context, widgetId: Int, date: LocalDate, today: LocalDate): PendingIntent {
        val zone = ZoneId.systemDefault()
        val begin = if (date == today) {
            Instant.now().atZone(zone).plusHours(1).withMinute(0).withSecond(0).withNano(0)
        } else {
            date.atTime(9, 0).atZone(zone)
        }
        val insert = Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin.toInstant().toEpochMilli())
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, begin.plusHours(1).toInstant().toEpochMilli())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val ok = canInsert ?: (insert.resolveActivity(context.packageManager) != null).also { canInsert = it }
        return PendingIntent.getActivity(context, widgetId, if (ok) insert else app(context), FLAGS)
    }

    private fun app(context: Context): Intent =
        (context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent())
            .setPackage(context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
}

/**
 * The taps of one widget's picture, made once and shared by all its sizes: every size lays its
 * targets where its own picture put them, but the same day opens the same thing.
 */
internal class CalendarClicks(private val context: Context, private val provider: ComponentName, private val widgetId: Int, val view: CalendarView) {
    private val days = HashMap<LocalDate, PendingIntent>()
    private val months = HashMap<Int, PendingIntent>()
    val add: PendingIntent by lazy { CalendarIntents.add(context, widgetId, if (view.isCurrentMonth) view.today else view.month.atDay(1), view.today) }

    fun day(date: LocalDate): PendingIntent = days.getOrPut(date) { CalendarIntents.day(context, widgetId, date) }

    fun month(delta: Int): PendingIntent = months.getOrPut(delta) { CalendarIntents.month(context, provider, widgetId, delta) }
}

/**
 * Lays the calendar's tap targets over its picture: invisible views where the picture shows
 * the arrows, the month's name, the new-event button and each day.
 */
internal fun RemoteViews.setCalendarTargets(context: Context, clicks: CalendarClicks, targets: CalendarTargets) {
    removeAllViews(R.id.widget_targets)
    fun add(rect: RectF, click: PendingIntent, description: String, id: Int) {
        val target = RemoteViews(context.packageName, R.layout.calendar_target)
        target.setViewLayoutWidth(R.id.calendar_target, rect.width(), TypedValue.COMPLEX_UNIT_DIP)
        target.setViewLayoutHeight(R.id.calendar_target, rect.height(), TypedValue.COMPLEX_UNIT_DIP)
        target.setViewLayoutMargin(R.id.calendar_target, RemoteViews.MARGIN_LEFT, rect.left, TypedValue.COMPLEX_UNIT_DIP)
        target.setViewLayoutMargin(R.id.calendar_target, RemoteViews.MARGIN_TOP, rect.top, TypedValue.COMPLEX_UNIT_DIP)
        target.setOnClickPendingIntent(R.id.calendar_target, click)
        target.setContentDescription(R.id.calendar_target, description)
        addStableView(R.id.widget_targets, target, id)
    }
    val view = clicks.view
    val dayFormat = java.time.format.DateTimeFormatter.ofPattern("d MMMM", view.locale)
    targets.days.forEachIndexed { i, (date, rect) ->
        add(rect, clicks.day(date), dayFormat.format(date), 100 + i)
    }
    targets.previous?.let { add(it, clicks.month(-1), context.getString(R.string.calendar_previous), 1) }
    targets.next?.let { add(it, clicks.month(1), context.getString(R.string.calendar_next), 2) }
    targets.title?.let {
        // On this month the name opens the calendar; elsewhere it brings you back.
        if (view.isCurrentMonth) {
            add(it, clicks.day(view.today), context.getString(R.string.calendar_open), 3)
        } else {
            add(it, clicks.month(0), context.getString(R.string.calendar_today), 3)
        }
    }
    targets.add?.let { add(it, clicks.add, context.getString(R.string.calendar_add), 4) }
}

/**
 * The widget as the launcher gets it from a drawn page: at each size its picture, what it says
 * to accessibility, the season moving over it and its tap targets. Nothing is drawn here.
 */
internal fun CalendarPages.Page.views(context: Context, provider: ComponentName, widgetId: Int, locale: Locale): RemoteViews {
    val view = CalendarView(month, today, locale = locale)
    val clicks = CalendarClicks(context, provider, widgetId, view)
    // What it says: the month, the day, and the week's picture by name.
    val week = SeasonClock.weekFor(month, today)
    val scene = context.resources.getStringArray(R.array.calendar_weeks).getOrNull(week - 1)
    val description = if (scene == null) view.spoken else view.spoken + ". " + context.getString(R.string.calendar_week_scene, week, scene)
    val bySize = sizes.associate { s ->
        s.size to RemoteViews(context.packageName, R.layout.widget_calendar).apply {
            setImageViewBitmap(R.id.widget_image, s.bitmap)
            setContentDescription(R.id.widget_image, description)
            setLiveWeather(context.packageName, live, s.size.width, s.size.height, radius)
            setCalendarTargets(context, clicks, s.targets)
        }
    }
    return if (bySize.size == 1) bySize.values.first() else RemoteViews(bySize)
}
