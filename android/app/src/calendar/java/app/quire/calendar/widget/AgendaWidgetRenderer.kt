package app.quire.calendar.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.util.TypedValue
import android.widget.RemoteViews
import app.quire.R
import app.quire.calendar.core.AgendaEntry
import app.quire.calendar.core.EventRepository
import app.quire.calendar.core.Prefs
import app.quire.calendar.core.Skin
import app.quire.calendar.core.WidgetPaint
import app.quire.calendar.m3.MainActivity
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * The agenda card: what is coming, in order.
 *
 * The month card answers "what shape is the month"; this one answers "what is next", which is
 * the other question a calendar is asked. It shares the month card's whole anatomy — surface,
 * palette, header, add glyph — so a pair of them on one home screen is one object at two jobs.
 *
 * It does not scroll, on purpose. A widget is a fixed rectangle the launcher hands out, and the
 * honest use of one is to show what fits and say what does not: the card counts what its height
 * can hold and ends with "+ N more" when the fortnight holds more than the box. Tapping any row
 * opens its day in the app, where scrolling is what screens are for.
 */
object AgendaWidgetRenderer {

    /** How far ahead the card looks. Past two weeks a list stops being "next" and becomes a year. */
    private const val HORIZON_DAYS = 14

    private const val NARROW_DP = 200

    /** Roughly what a line of this face occupies, as a multiple of its type size. */
    private const val LINE_HEIGHT = 1.35f

    /** A character of the time column, as a fraction of its type size. */
    private const val TIME_CHAR_WIDTH = 0.62f

    private fun px(context: Context, dp: Float): Int =
        (dp * context.resources.displayMetrics.density + 0.5f).toInt()

    fun build(context: Context, manager: AppWidgetManager, widgetId: Int): RemoteViews {
        val options = manager.getAppWidgetOptions(widgetId)
        val portrait = context.resources.configuration.orientation !=
            Configuration.ORIENTATION_LANDSCAPE
        val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
            .takeIf { it > 0 } ?: 250
        val heightKey = if (portrait) {
            AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT
        } else {
            AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT
        }
        val heightDp = options.getInt(heightKey, 0).takeIf { it > 0 }
            ?: options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0).takeIf { it > 0 }
            ?: 180
        return build(context, widgetId, widthDp, heightDp)
    }

    fun build(context: Context, widgetId: Int, widthDp: Int, heightDp: Int): RemoteViews {
        val prefs = Prefs.get(context)
        val wp = prefs.widget(widgetId)
        val paint = WidgetPaint.of(context, wp.skin, wp.accent, wp.dynamic)
        val filled = wp.skin == Skin.COLOUR
        val locale = Locale.getDefault()
        val today = LocalDate.now()

        val root = RemoteViews(context.packageName, R.layout.agenda_widget)

        paint.tint(root, R.id.surface, "setColorFilter", R.color.widget_surface) { it.surface }
        root.setInt(R.id.surface, "setImageAlpha", wp.opacity * 255 / 100)
        if (!filled) {
            paint.tint(root, R.id.surface_border, "setColorFilter", R.color.widget_hairline_strong) {
                it.hairlineStrong
            }
        }
        root.setViewVisibility(
            R.id.surface_border,
            if (filled) android.view.View.GONE else android.view.View.VISIBLE,
        )

        val narrow = widthDp < NARROW_DP
        val padDp = if (narrow) 9f else 12f
        val pad = px(context, padDp)
        root.setViewPadding(R.id.content, pad, pad, pad, px(context, padDp - 2f))

        val scale = context.resources.configuration.fontScale.coerceIn(0.85f, 2f)
        // The month card's own title formula: the two headers must be the same header.
        val titleSp = minOf(
            widthDp * (if (filled) 0.075f else 0.055f),
            widthDp * (if (filled) 0.085f else 0.063f) / scale,
        ).coerceIn(11f, 24f)
        val navDp = (titleSp * 1.85f).coerceIn(21f, 30f)

        // Today's name is the card's title, because the card starts at today: the first section
        // needs no label of its own, the header already is one.
        root.setTextViewText(
            R.id.agenda_title,
            today.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() },
        )
        paint.tint(root, R.id.agenda_title, "setTextColor", R.color.widget_ink) { it.ink }
        root.setTextViewTextSize(R.id.agenda_title, TypedValue.COMPLEX_UNIT_SP, titleSp)
        root.setTextViewText(
            R.id.agenda_date,
            java.time.format.DateTimeFormatter.ofPattern("d MMMM", locale).format(today),
        )
        paint.tint(root, R.id.agenda_date, "setTextColor", R.color.widget_ink_faint) { it.inkFaint }
        root.setTextViewTextSize(R.id.agenda_date, TypedValue.COMPLEX_UNIT_SP, titleSp)
        root.setViewVisibility(
            R.id.agenda_date,
            if (narrow) android.view.View.GONE else android.view.View.VISIBLE,
        )

        paint.tint(root, R.id.add_button, "setColorFilter", R.color.widget_accent) { it.accent }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            root.setViewLayoutWidth(R.id.add_button, navDp, TypedValue.COMPLEX_UNIT_DIP)
            root.setViewLayoutHeight(R.id.add_button, navDp, TypedValue.COMPLEX_UNIT_DIP)
        }
        root.setOnClickPendingIntent(R.id.add_button, composeIntent(context, widgetId))
        root.setOnClickPendingIntent(R.id.widget_root, openIntent(context, widgetId, today))

        // ---- the list, sized from the box -------------------------------

        val rowSp = minOf(widthDp * 0.040f, widthDp * 0.046f / scale).coerceIn(10f, 13f)
        val labelSp = (rowSp * 0.80f).coerceIn(8.5f, 10.5f)
        val rowGapDp = 3f
        val labelTopDp = 7f
        val rowDp = rowSp * LINE_HEIGHT * scale + rowGapDp
        val labelDp = labelSp * LINE_HEIGHT * scale + labelTopDp
        val headerDp = maxOf(navDp, titleSp * 1.45f * scale)
        var roomDp = heightDp - (2 * padDp - 2f) - headerDp - 4f

        root.removeAllViews(R.id.agenda_days)

        if (!EventRepository.hasPermission(context)) {
            // An empty list would be a lie here: nothing was read, not nothing found.
            root.addView(
                R.id.agenda_days,
                line(context, paint, context.getString(R.string.agenda_no_access), labelSp, labelTopDp, muted = true),
            )
            return root
        }

        val loads = EventRepository.upcoming(context, today, HORIZON_DAYS, prefs.hiddenCalendars)
        val total = loads.values.sumOf { it.size }
        if (total == 0) {
            root.addView(
                R.id.agenda_days,
                line(context, paint, context.getString(R.string.agenda_empty), labelSp, labelTopDp, muted = false),
            )
            return root
        }

        // The time column is as wide as the widest time it will hold, measured from a real
        // formatted midnight-ish sample rather than assumed — a 12-hour locale writes "11:50 PM"
        // and a column sized for "23:50" would fold it.
        val clock = android.text.format.DateFormat.getTimeFormat(context)
        val sample = clock.format(
            java.util.Date.from(
                today.atTime(23, 50).atZone(java.time.ZoneId.systemDefault()).toInstant(),
            ),
        )
        val timeColDp = sample.length * rowSp * TIME_CHAR_WIDTH * scale + 4f

        // The list is laid out on paper before any view exists: every line the fortnight wants,
        // in order, with its height. If the whole plan fits it is rendered whole; if not, the
        // tail line that says what was cut reserves its own height first — so the tail can
        // never itself be the thing that gets clipped.
        val atoms = ArrayList<Pair<LocalDate, AgendaEntry?>>()
        var date = today
        while (!date.isAfter(today.plusDays((HORIZON_DAYS - 1).toLong()))) {
            val entries = loads[date]
            if (!entries.isNullOrEmpty()) {
                // Today needs no label of its own: the card's title already is one.
                if (date != today) atoms.add(date to null)
                entries.forEach { atoms.add(date to it) }
            }
            date = date.plusDays(1)
        }
        fun heightOf(atom: Pair<LocalDate, AgendaEntry?>) =
            if (atom.second == null) labelDp else rowDp
        val wholeDp = atoms.map { heightOf(it) }.sum()
        var budget = if (wholeDp > roomDp) roomDp - labelDp else roomDp

        var shown = 0
        for ((index, atom) in atoms.withIndex()) {
            val need = heightOf(atom)
            if (budget < need) break
            val entry = atom.second
            if (entry == null) {
                // A label with no room for even one row under it is a heading for nothing.
                val under = atoms.getOrNull(index + 1)
                if (under != null && budget < need + heightOf(under)) break
                root.addView(
                    R.id.agenda_days,
                    line(context, paint, dayLabel(context, atom.first, today, locale), labelSp, labelTopDp, muted = false),
                )
            } else {
                root.addView(
                    R.id.agenda_days,
                    row(context, paint, entry, clock, rowSp, rowGapDp, timeColDp, widgetId, atom.first),
                )
                shown++
            }
            budget -= need
        }

        if (shown < total) {
            root.addView(
                R.id.agenda_days,
                line(
                    context, paint,
                    context.getString(R.string.agenda_more, total - shown),
                    labelSp, labelTopDp, muted = true,
                ),
            )
        }

        root.setContentDescription(
            R.id.widget_root,
            context.getString(R.string.agenda_widget_label),
        )
        return root
    }

    /** "Tomorrow" by name, further days by weekday and date — the way a person says them. */
    private fun dayLabel(
        context: Context,
        date: LocalDate,
        today: LocalDate,
        locale: Locale,
    ): String {
        if (date == today.plusDays(1)) return context.getString(R.string.tomorrow)
        return buildString {
            append(
                date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() },
            )
            append(' ')
            append(date.dayOfMonth)
            if (date.month != today.month) {
                append(' ')
                append(java.time.format.DateTimeFormatter.ofPattern("MMM", locale).format(date))
            }
        }
    }

    private fun line(
        context: Context,
        paint: WidgetPaint,
        text: String,
        sp: Float,
        topDp: Float,
        muted: Boolean,
    ): RemoteViews {
        val view = RemoteViews(context.packageName, R.layout.agenda_line)
        view.setTextViewText(R.id.line_text, text)
        view.setTextViewTextSize(R.id.line_text, TypedValue.COMPLEX_UNIT_SP, sp)
        view.setViewPadding(R.id.line_text, 0, px(context, topDp), 0, 0)
        paint.tint(
            view, R.id.line_text, "setTextColor",
            if (muted) R.color.widget_ink_ghost else R.color.widget_ink_faint,
        ) { if (muted) it.inkGhost else it.inkFaint }
        return view
    }

    private fun row(
        context: Context,
        paint: WidgetPaint,
        entry: AgendaEntry,
        clock: java.text.DateFormat,
        sp: Float,
        gapDp: Float,
        timeColDp: Float,
        widgetId: Int,
        date: LocalDate,
    ): RemoteViews {
        val view = RemoteViews(context.packageName, R.layout.agenda_row)
        view.setViewPadding(R.id.row_root, 0, px(context, gapDp), 0, 0)

        // The calendar's own colour is data, not palette: both faces wear it as it is.
        if (entry.colour != 0) {
            paint.fixed(view, R.id.row_chip, "setColorFilter", entry.colour)
        } else {
            paint.tint(view, R.id.row_chip, "setColorFilter", R.color.widget_accent) { it.accent }
        }

        // An all-day entry has no time to give the column, and a dash pretending to be one is
        // noise: the column is simply absent and the title starts at the bar, which is itself
        // the mark of an all-day entry once you have seen two rows.
        if (entry.allDay) {
            view.setViewVisibility(R.id.row_time, android.view.View.GONE)
        } else {
            view.setViewVisibility(R.id.row_time, android.view.View.VISIBLE)
            view.setTextViewText(R.id.row_time, clock.format(java.util.Date(entry.begin)))
            view.setTextViewTextSize(R.id.row_time, TypedValue.COMPLEX_UNIT_SP, sp)
            paint.tint(view, R.id.row_time, "setTextColor", R.color.widget_ink_muted) { it.inkMuted }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                view.setViewLayoutWidth(R.id.row_time, timeColDp, TypedValue.COMPLEX_UNIT_DIP)
            }
        }

        view.setTextViewText(
            R.id.row_title,
            entry.title.ifBlank { context.getString(R.string.agenda_untitled) },
        )
        view.setTextViewTextSize(R.id.row_title, TypedValue.COMPLEX_UNIT_SP, sp)
        paint.tint(view, R.id.row_title, "setTextColor", R.color.widget_ink) { it.ink }

        view.setOnClickPendingIntent(R.id.row_root, openIntent(context, widgetId, date))
        return view
    }

    // ---- intents -------------------------------------------------------

    private const val FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    private fun openIntent(context: Context, widgetId: Int, date: LocalDate): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("quire://day/$date")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(context, widgetId * 64 + date.dayOfMonth, intent, FLAGS)
    }

    /** The add glyph composes in whatever calendar app is installed, for today. */
    private fun composeIntent(context: Context, widgetId: Int): PendingIntent {
        val start = LocalDate.now().atTime(9, 0)
            .atZone(java.time.ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = android.provider.CalendarContract.Events.CONTENT_URI
            putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
            putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, start + 3_600_000L)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return PendingIntent.getActivity(context, widgetId * 64 + 63, intent, FLAGS)
    }
}
