package com.monthcalendar.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * Material 3 Expressive month-view calendar widget.
 *
 * Features beyond a plain month grid:
 *  - Real device events (READ_CALENDAR) — coloured dots per day + an agenda of
 *    upcoming events on the large size.
 *  - Month navigation (‹ today ›) with per-widget state, so two widgets can show
 *    different months.
 *  - Material You dynamic colour (or a fixed accent), tonal containers, large
 *    rounded surfaces, a filled-primary "today" chip.
 *  - Adaptive size via [SizeMode.Responsive]: compact / medium / large.
 */
class CalendarWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    private val small = DpSize(170.dp, 170.dp)
    private val medium = DpSize(260.dp, 220.dp)
    private val large = DpSize(300.dp, 340.dp)

    override val sizeMode = SizeMode.Responsive(setOf(small, medium, large))

    private data class Layout(
        val tier: Int,            // 0 = small, 1 = medium, 2 = large
        val titleSize: Int,
        val headerSize: Int,
        val daySize: Int,
        val circle: Int,
        val dot: Int,
        val maxDots: Int,
        val pad: Int,
        val showNav: Boolean,
        val showAgenda: Boolean,
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val settings = CalendarSettingsStore(context).get()
        val state: Preferences = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val offset = state[MONTH_OFFSET] ?: 0

        val today = LocalDate.now()
        val anchor = YearMonth.now().plusMonths(offset.toLong())
        val month = CalendarModel.monthFor(anchor, today, settings.mondayFirst)

        val hasPerm = CalendarRepository.hasPermission(context)
        val eventsByDay = if (settings.showEvents && hasPerm) {
            CalendarRepository.eventsByDay(context, month.gridStart, month.gridEnd)
        } else {
            emptyMap()
        }
        val agenda = if (settings.showEvents && hasPerm) {
            CalendarRepository.upcoming(context, today)
        } else {
            emptyList()
        }

        // Resolve the colour scheme up front (context is available here):
        //  - DYNAMIC  → Material You from the live system palette (Android 12+),
        //    or null on older devices to fall back to baseline Material 3.
        //  - fixed    → the chosen accent scheme.
        val colors = if (settings.accent == Accent.DYNAMIC) {
            AccentSchemes.dynamic(context)
        } else {
            AccentSchemes.providersFor(settings.accent)
        }

        provideContent {
            val content: @Composable () -> Unit = {
                Content(month, today, settings, eventsByDay, agenda, hasPerm)
            }
            if (colors != null) {
                GlanceTheme(colors = colors, content = content)
            } else {
                GlanceTheme(content = content)
            }
        }
    }

    @Composable
    private fun Content(
        month: MonthData,
        today: LocalDate,
        settings: CalendarSettings,
        eventsByDay: Map<LocalDate, List<EventLite>>,
        agenda: List<EventLite>,
        hasPerm: Boolean,
    ) {
        val size = androidx.glance.LocalSize.current
        // Circle diameters are kept well below the per-row height budget for the
        // *bucket* size (6 rows + chrome must fit the bucket; the real widget is
        // always ≥ bucket, so it never clips). Dots/agenda are only enabled when
        // there is vertical room for them.
        val layout = when {
            size.height < 200.dp -> Layout(0, 15, 9, 10, 15, 4, 0, 12, showNav = size.width >= 240.dp, showAgenda = false)
            size.height < 290.dp -> Layout(1, 18, 10, 11, 16, 4, 3, 14, showNav = true, showAgenda = false)
            else -> Layout(2, 20, 10, 11, 16, 4, 3, 16, showNav = true, showAgenda = true)
        }

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(28.dp)
                .padding(layout.pad.dp),
        ) {
            Header(month, layout)
            Spacer(GlanceModifier.height(if (layout.tier == 0) 6.dp else 8.dp))
            WeekdayRow(month, layout)
            Spacer(GlanceModifier.height(4.dp))
            Grid(month, eventsByDay, layout)

            if (layout.showAgenda) {
                Spacer(GlanceModifier.height(10.dp))
                Agenda(agenda, today, hasPerm && settings.showEvents)
            } else if (settings.showEvents && !hasPerm) {
                Spacer(GlanceModifier.height(6.dp))
                PermissionHint()
            }
        }
    }

    @Composable
    private fun Header(month: MonthData, layout: Layout) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = month.title,
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = layout.titleSize.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = if (layout.showNav) TextAlign.Start else TextAlign.Center,
                ),
            )
            if (layout.showNav) {
                val btn = 28
                NavButton(R.drawable.ic_chevron_left, "Предыдущий месяц", actionRunCallback<ShiftMonthAction>(shiftParams(-1)), btn)
                Spacer(GlanceModifier.width(6.dp))
                NavButton(R.drawable.ic_today, "Текущий месяц", actionRunCallback<ResetMonthAction>(), btn, accent = true)
                Spacer(GlanceModifier.width(6.dp))
                NavButton(R.drawable.ic_chevron_right, "Следующий месяц", actionRunCallback<ShiftMonthAction>(shiftParams(1)), btn)
            }
        }
    }

    @Composable
    private fun NavButton(icon: Int, desc: String, onClick: Action, sizeDp: Int, accent: Boolean = false) {
        val bg = if (accent) GlanceTheme.colors.primaryContainer else GlanceTheme.colors.secondaryContainer
        val fg = if (accent) GlanceTheme.colors.onPrimaryContainer else GlanceTheme.colors.onSecondaryContainer
        Box(
            modifier = GlanceModifier
                .size(sizeDp.dp)
                .cornerRadius((sizeDp / 2).dp)
                .background(bg)
                .clickable(onClick),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(icon),
                contentDescription = desc,
                colorFilter = ColorFilter.tint(fg),
                modifier = GlanceModifier.size((sizeDp * 0.58).toInt().dp),
            )
        }
    }

    @Composable
    private fun WeekdayRow(month: MonthData, layout: Layout) {
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            month.weekdayHeaders.forEachIndexed { i, label ->
                val weekend = (month.weekdayHeaders[i] == "Сб" || month.weekdayHeaders[i] == "Вс")
                Text(
                    text = label,
                    modifier = GlanceModifier.defaultWeight(),
                    style = TextStyle(
                        color = if (weekend) GlanceTheme.colors.tertiary else GlanceTheme.colors.onSurfaceVariant,
                        fontSize = layout.headerSize.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                    ),
                )
            }
        }
    }

    @Composable
    private fun androidx.glance.layout.ColumnScope.Grid(
        month: MonthData,
        eventsByDay: Map<LocalDate, List<EventLite>>,
        layout: Layout,
    ) {
        Column(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
            month.weeks.forEach { week ->
                Row(
                    modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    week.forEach { cell ->
                        DayCellView(cell, eventsByDay[cell.date].orEmpty(), layout)
                    }
                }
            }
        }
    }

    @Composable
    private fun androidx.glance.layout.RowScope.DayCellView(
        cell: DayCell,
        events: List<EventLite>,
        layout: Layout,
    ) {
        Box(
            modifier = GlanceModifier
                .defaultWeight()
                .fillMaxHeight()
                .clickable(actionStartActivity(dayIntent(cell.date))),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                DayNumber(cell, layout)
                if (events.isNotEmpty() && layout.maxDots > 0) {
                    Spacer(GlanceModifier.height(1.dp))
                    DotRow(events, layout)
                }
            }
        }
    }

    /**
     * The day number. Today is a neat filled-primary circle whose diameter is
     * chosen per size tier to be larger than the glyph yet smaller than the row
     * height — so it reads as a clean disc and never clips the digits.
     */
    @Composable
    private fun DayNumber(cell: DayCell, layout: Layout) {
        if (cell.isToday) {
            Box(
                modifier = GlanceModifier
                    .size(layout.circle.dp)
                    .cornerRadius((layout.circle / 2).dp)
                    .background(GlanceTheme.colors.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = cell.day.toString(),
                    style = TextStyle(
                        color = GlanceTheme.colors.onPrimary,
                        fontSize = layout.daySize.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    ),
                )
            }
        } else {
            val color = when {
                !cell.inCurrentMonth -> GlanceTheme.colors.onSurfaceVariant
                cell.isWeekend -> GlanceTheme.colors.tertiary
                else -> GlanceTheme.colors.onSurface
            }
            Text(
                text = cell.day.toString(),
                style = TextStyle(
                    color = color,
                    fontSize = layout.daySize.sp,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center,
                ),
            )
        }
    }

    @Composable
    private fun DotRow(events: List<EventLite>, layout: Layout) {
        val colors = events.map { dotColor(it) }.distinct().take(layout.maxDots)
        Row(verticalAlignment = Alignment.CenterVertically) {
            colors.forEachIndexed { i, c ->
                if (i > 0) Spacer(GlanceModifier.width(2.dp))
                Box(
                    modifier = GlanceModifier
                        .size(layout.dot.dp)
                        .cornerRadius((layout.dot / 2).dp)
                        .background(c),
                ) {}
            }
        }
    }

    @Composable
    private fun dotColor(e: EventLite): ColorProvider =
        if (e.color != 0) ColorProvider(Color(e.color)) else GlanceTheme.colors.primary

    @Composable
    private fun Agenda(agenda: List<EventLite>, today: LocalDate, enabled: Boolean) {
        if (!enabled) {
            PermissionHint()
            return
        }
        if (agenda.isEmpty()) {
            Text(
                text = "Нет ближайших событий",
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
            )
            return
        }
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            agenda.take(3).forEachIndexed { i, e ->
                if (i > 0) Spacer(GlanceModifier.height(4.dp))
                AgendaItem(e, today)
            }
        }
    }

    /** Compact single-line chip: colour dot · time · title — keeps the grid roomy. */
    @Composable
    private fun AgendaItem(e: EventLite, today: LocalDate) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .cornerRadius(14.dp)
                .background(GlanceTheme.colors.secondaryContainer)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = GlanceModifier
                    .size(8.dp)
                    .cornerRadius(4.dp)
                    .background(dotColor(e)),
            ) {}
            Spacer(GlanceModifier.width(8.dp))
            Text(
                text = agendaWhen(e, today),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
            )
            Spacer(GlanceModifier.width(8.dp))
            Text(
                text = e.title,
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(
                    color = GlanceTheme.colors.onSecondaryContainer,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
    }

    @Composable
    private fun PermissionHint() {
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .cornerRadius(16.dp)
                .background(GlanceTheme.colors.secondaryContainer)
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .clickable(actionStartActivity(appIntent())),
        ) {
            Text(
                text = "Нажмите, чтобы показать события календаря",
                style = TextStyle(color = GlanceTheme.colors.onSecondaryContainer, fontSize = 12.sp),
            )
        }
    }

    private fun dayIntent(date: LocalDate): Intent {
        val ms = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("content://com.android.calendar/time/$ms")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    private fun appIntent(): Intent =
        Intent(Intent.ACTION_MAIN).setClassName(
            "com.monthcalendar.widget", "com.monthcalendar.widget.MainActivity",
        ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
}

private fun agendaWhen(e: EventLite, today: LocalDate): String {
    val d = e.date
    val dayLabel = when (d) {
        today -> "Сегодня"
        today.plusDays(1) -> "Завтра"
        else -> "${d.dayOfMonth} ${CalendarModel.monthName(d.monthValue).take(3).lowercase()}"
    }
    if (e.allDay) return "$dayLabel · весь день"
    val t = Instant.ofEpochMilli(e.begin).atZone(ZoneId.systemDefault())
    val time = "%02d:%02d".format(t.hour, t.minute)
    return "$dayLabel · $time"
}
