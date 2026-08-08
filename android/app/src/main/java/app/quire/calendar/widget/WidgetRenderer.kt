package app.quire.calendar.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.util.TypedValue
import android.widget.RemoteViews
import app.quire.calendar.R
import app.quire.calendar.core.EventRepository
import app.quire.calendar.core.MonthModel
import app.quire.calendar.core.Palette
import app.quire.calendar.core.Prefs
import app.quire.calendar.core.Tokens
import app.quire.calendar.core.WidgetPrefs
import app.quire.calendar.ui.MainActivity
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

/**
 * Builds the widget's RemoteViews tree.
 *
 * The six week rows are added at runtime rather than declared, so each row and
 * each cell is its own RemoteViews object — duplicate ids across siblings are
 * fine, and every square can carry its own tap target without pre-declaring
 * forty-two ids in `ids.xml`.
 */
object WidgetRenderer {

    private val WEEKDAY_IDS = intArrayOf(
        R.id.wd_0, R.id.wd_1, R.id.wd_2, R.id.wd_3, R.id.wd_4, R.id.wd_5, R.id.wd_6,
    )
    private val DOT_IDS = intArrayOf(R.id.dot_0, R.id.dot_1, R.id.dot_2)
    private val NAV_IDS = intArrayOf(R.id.nav_prev, R.id.nav_today, R.id.nav_next)

    private fun px(context: Context, dp: Float): Int =
        (dp * context.resources.displayMetrics.density + 0.5f).toInt()

    /** Below this the card is half a home-screen row wide: tighten everything. */
    private const val NARROW_DP = 200

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
            ?: 220
        return build(context, widgetId, widthDp, heightDp)
    }

    /**
     * The size is passed in rather than read here so the same code path can be
     * driven at any placement — a two-cell card, a full-width one — under test.
     */
    fun build(context: Context, widgetId: Int, widthDp: Int, heightDp: Int): RemoteViews {
        val prefs = Prefs.get(context)
        val wp = prefs.widget(widgetId)
        val dark = Tokens.resolveDark(context, wp.skin)
        val palette = Tokens.palette(dark, wp.accent)
        val locale = Locale.getDefault()

        val today = LocalDate.now()
        val month = YearMonth.from(today).plusMonths(wp.monthOffset.toLong())
        val firstDay = MonthModel.firstDayOfWeek(prefs.firstDay, locale)
        val cells = MonthModel.cells(month, firstDay)

        val loads = if (wp.showEvents) {
            EventRepository.loadFor(
                context = context,
                from = cells.first(),
                days = MonthModel.CELLS,
                hidden = prefs.hiddenCalendars,
            )
        } else {
            emptyMap()
        }

        val narrow = widthDp < NARROW_DP
        val padDp = if (narrow) 9f else 12f
        val titleSp = (widthDp * 0.055f).coerceIn(12f, 19f)
        val weekdaySp = (titleSp * 0.62f).coerceIn(8f, 11f)
        val navDp = (titleSp * 1.85f).coerceIn(21f, 30f)

        // Header row, weekday strip, rule and its margins, plus the card padding.
        val chromeDp = 2 * padDp + navDp + weekdaySp * 1.8f + 12f
        val rowHeightDp = ((heightDp - chromeDp) / MonthModel.ROWS).coerceAtLeast(12f)
        val compact = rowHeightDp < 26f
        val markDp = (rowHeightDp * 0.72f).coerceIn(12f, 27f)
        val daySp = (rowHeightDp * 0.42f).coerceIn(8.5f, 14f)
        val showDots = wp.showEvents && rowHeightDp >= 21f
        val showYear = !narrow

        val root = RemoteViews(context.packageName, R.layout.widget_month)

        root.setInt(R.id.surface, "setColorFilter", palette.surface)
        root.setInt(R.id.surface, "setImageAlpha", wp.opacity * 255 / 100)
        root.setInt(R.id.surface_border, "setColorFilter", palette.hairlineStrong)
        root.setInt(R.id.header_rule, "setBackgroundColor", palette.hairline)

        val pad = px(context, padDp)
        root.setViewPadding(R.id.content, pad, pad, pad, px(context, padDp - 2f))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            for (id in NAV_IDS) {
                root.setViewLayoutWidth(id, navDp, TypedValue.COMPLEX_UNIT_DIP)
                root.setViewLayoutHeight(id, navDp, TypedValue.COMPLEX_UNIT_DIP)
            }
        }

        root.setTextViewText(R.id.month_title, MonthModel.monthName(month, locale))
        root.setTextColor(R.id.month_title, palette.ink)
        root.setTextViewTextSize(R.id.month_title, TypedValue.COMPLEX_UNIT_SP, titleSp)
        root.setTextViewText(R.id.year_title, month.year.toString())
        root.setTextColor(R.id.year_title, palette.inkFaint)
        root.setTextViewTextSize(R.id.year_title, TypedValue.COMPLEX_UNIT_SP, titleSp)
        root.setViewVisibility(R.id.year_title, if (showYear) android.view.View.VISIBLE else android.view.View.GONE)

        root.setInt(R.id.nav_prev, "setColorFilter", palette.inkFaint)
        root.setInt(R.id.nav_next, "setColorFilter", palette.inkFaint)
        root.setInt(
            R.id.nav_today,
            "setColorFilter",
            if (wp.monthOffset == 0) palette.inkGhost else palette.accent,
        )
        root.setOnClickPendingIntent(R.id.nav_prev, navIntent(context, widgetId, MonthWidgetProvider.ACTION_PREV))
        root.setOnClickPendingIntent(R.id.nav_next, navIntent(context, widgetId, MonthWidgetProvider.ACTION_NEXT))
        root.setOnClickPendingIntent(R.id.nav_today, navIntent(context, widgetId, MonthWidgetProvider.ACTION_TODAY))
        root.setOnClickPendingIntent(R.id.title_block, openIntent(context, widgetId, month.atDay(1), monthOnly = true))

        val labels = MonthModel.weekdayLabels(firstDay, locale)
        val order = MonthModel.weekdayOrder(firstDay)
        for (i in WEEKDAY_IDS.indices) {
            root.setTextViewText(WEEKDAY_IDS[i], labels[i])
            root.setTextViewTextSize(WEEKDAY_IDS[i], TypedValue.COMPLEX_UNIT_SP, weekdaySp)
            root.setTextColor(
                WEEKDAY_IDS[i],
                if (wp.dimWeekends && MonthModel.isWeekend(order[i])) palette.inkGhost else palette.inkFaint,
            )
        }
        root.setViewVisibility(
            R.id.wd_gutter,
            if (wp.weekNumbers) android.view.View.VISIBLE else android.view.View.GONE,
        )

        root.removeAllViews(R.id.weeks)
        val cellLayout = if (compact) R.layout.widget_cell_small else R.layout.widget_cell

        for (row in 0 until MonthModel.ROWS) {
            val week = RemoteViews(context.packageName, R.layout.widget_week)
            if (row == 0) {
                week.setViewVisibility(R.id.week_rule, android.view.View.GONE)
            } else {
                week.setInt(R.id.week_rule, "setBackgroundColor", palette.hairline)
            }

            if (wp.weekNumbers) {
                val number = RemoteViews(context.packageName, R.layout.widget_week_number)
                number.setTextViewText(
                    R.id.week_number,
                    MonthModel.weekOfYear(cells[row * MonthModel.COLUMNS], locale).toString(),
                )
                number.setTextColor(R.id.week_number, palette.inkGhost)
                number.setTextViewTextSize(
                    R.id.week_number,
                    TypedValue.COMPLEX_UNIT_SP,
                    (weekdaySp - 0.5f).coerceAtLeast(7f),
                )
                week.addView(R.id.week_cells, number)
            }

            for (column in 0 until MonthModel.COLUMNS) {
                val date = cells[row * MonthModel.COLUMNS + column]
                val cell = RemoteViews(context.packageName, cellLayout)
                paintCell(
                    context = context,
                    cell = cell,
                    date = date,
                    month = month,
                    today = today,
                    palette = palette,
                    prefs = wp,
                    load = loads[date],
                    daySp = daySp,
                    markDp = markDp,
                    showDots = showDots,
                    widgetId = widgetId,
                )
                week.addView(R.id.week_cells, cell)
            }
            root.addView(R.id.weeks, week)
        }

        root.setContentDescription(
            R.id.widget_root,
            "${MonthModel.monthName(month, locale)} ${month.year}",
        )
        return root
    }

    private fun paintCell(
        context: Context,
        cell: RemoteViews,
        date: LocalDate,
        month: YearMonth,
        today: LocalDate,
        palette: Palette,
        prefs: WidgetPrefs,
        load: app.quire.calendar.core.DayLoad?,
        daySp: Float,
        markDp: Float,
        showDots: Boolean,
        widgetId: Int,
    ) {
        val inMonth = date.year == month.year && date.month == month.month
        val visible = inMonth || prefs.showAdjacent
        val isToday = date == today

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            cell.setViewLayoutWidth(R.id.cell_mark, markDp, TypedValue.COMPLEX_UNIT_DIP)
            cell.setViewLayoutHeight(R.id.cell_mark, markDp, TypedValue.COMPLEX_UNIT_DIP)
            cell.setViewLayoutWidth(R.id.cell_text, markDp, TypedValue.COMPLEX_UNIT_DIP)
            cell.setViewLayoutHeight(R.id.cell_text, markDp, TypedValue.COMPLEX_UNIT_DIP)
        }

        if (!visible) {
            cell.setTextViewText(R.id.cell_text, "")
            cell.setViewVisibility(R.id.cell_mark, android.view.View.INVISIBLE)
            cell.setViewVisibility(
                R.id.cell_dots,
                if (showDots) android.view.View.INVISIBLE else android.view.View.GONE,
            )
            return
        }

        cell.setTextViewText(R.id.cell_text, date.dayOfMonth.toString())
        cell.setTextViewTextSize(R.id.cell_text, TypedValue.COMPLEX_UNIT_SP, daySp)
        cell.setTextColor(
            R.id.cell_text,
            when {
                isToday -> palette.onAccent
                !inMonth -> palette.inkGhost
                prefs.dimWeekends && MonthModel.isWeekend(date.dayOfWeek) -> palette.inkMuted
                else -> palette.ink
            },
        )

        if (isToday) {
            cell.setViewVisibility(R.id.cell_mark, android.view.View.VISIBLE)
            cell.setInt(R.id.cell_mark, "setColorFilter", palette.accent)
        } else {
            cell.setViewVisibility(R.id.cell_mark, android.view.View.INVISIBLE)
        }

        val count = load?.count ?: 0
        if (!showDots) {
            // Marks are switched off entirely: give the row back to the numbers.
            cell.setViewVisibility(R.id.cell_dots, android.view.View.GONE)
        } else if (count == 0) {
            // Marks are on but this day is empty: hold the space so the column
            // of numbers stays on one baseline.
            cell.setViewVisibility(R.id.cell_dots, android.view.View.INVISIBLE)
        } else {
            cell.setViewVisibility(R.id.cell_dots, android.view.View.VISIBLE)
            val colours = load?.colours ?: IntArray(0)
            val shown = minOf(
                count,
                3,
                if (prefs.colouredDots) maxOf(colours.size, 1) else 1,
            )
            for (i in DOT_IDS.indices) {
                if (i < shown) {
                    cell.setViewVisibility(DOT_IDS[i], android.view.View.VISIBLE)
                    cell.setInt(
                        DOT_IDS[i],
                        "setColorFilter",
                        when {
                            !inMonth -> palette.inkGhost
                            prefs.colouredDots && i < colours.size -> colours[i]
                            else -> palette.inkFaint
                        },
                    )
                } else {
                    cell.setViewVisibility(DOT_IDS[i], android.view.View.GONE)
                }
            }
        }

        cell.setOnClickPendingIntent(R.id.cell_root, openIntent(context, widgetId, date, monthOnly = false))
    }

    // ---- intents -------------------------------------------------------

    private const val FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    private fun navIntent(context: Context, widgetId: Int, action: String): PendingIntent {
        val intent = Intent(context, MonthWidgetProvider::class.java).apply {
            this.action = action
            component = ComponentName(context, MonthWidgetProvider::class.java)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            data = Uri.parse("quire://widget/$widgetId/$action")
        }
        return PendingIntent.getBroadcast(context, widgetId, intent, FLAGS)
    }

    private fun openIntent(
        context: Context,
        widgetId: Int,
        date: LocalDate,
        monthOnly: Boolean,
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("quire://${if (monthOnly) "month" else "day"}/$date")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val request = widgetId * 64 + date.dayOfMonth + if (monthOnly) 40 else 0
        return PendingIntent.getActivity(context, request, intent, FLAGS)
    }
}
