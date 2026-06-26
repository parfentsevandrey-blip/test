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
import androidx.glance.LocalContext
import androidx.glance.LocalSize
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
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
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
import com.monthcalendar.widget.design.D
import com.monthcalendar.widget.design.Eyebrow
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * Month-view calendar widget.
 *
 * - Live Material You via [AccentSchemes] (resource-backed providers) or a fixed
 *   accent; one strong colour moment per view — the filled-primary "today" disc.
 * - Real device events (READ_CALENDAR): per-day dots + an upcoming agenda.
 * - Per-widget month navigation, adaptive across compact/medium/large.
 */
class CalendarWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    private val small = DpSize(170.dp, 170.dp)
    private val medium = DpSize(260.dp, 220.dp)
    private val large = DpSize(300.dp, 340.dp)

    override val sizeMode = SizeMode.Responsive(setOf(small, medium, large))

    /** Per-tier sizing, kept so the today disc always fits within a grid row. */
    private data class Tier(
        val index: Int,
        val month: Int,
        val weekday: Int,
        val day: Int,
        val disc: Int,
        val dot: Int,
        val maxDots: Int,
        val pad: Int,
        val nav: Boolean,
        val agenda: Boolean,
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

        // Live for DYNAMIC: DynamicThemeColorProviders is resource-backed and
        // re-resolves the system palette in the launcher at draw time.
        val colors = AccentSchemes.providersFor(settings.accent)

        provideContent {
            GlanceTheme(colors = colors) {
                Content(month, today, settings, eventsByDay, agenda, hasPerm)
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
        val h = LocalSize.current.height
        val tier = when {
            h < 200.dp -> Tier(0, 17, 11, 13, 15, 4, 0, 14, nav = false, agenda = false)
            h < 290.dp -> Tier(1, 20, 11, 13, 17, 4, 3, 16, nav = true, agenda = false)
            else -> Tier(2, 22, 11, 14, 18, 4, 3, 16, nav = true, agenda = true)
        }

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ImageProvider(R.drawable.calendar_widget_bg))
                .cornerRadius(D.rXl)
                .padding(tier.pad.dp),
        ) {
            Header(month, tier)
            Spacer(GlanceModifier.height(D.s4))
            WeekdayRow(month, tier)
            Spacer(GlanceModifier.height(D.s3))
            Grid(month, eventsByDay, tier)

            if (tier.agenda) {
                Spacer(GlanceModifier.height(D.s5))
                if (hasPerm && settings.showEvents) Agenda(agenda) else if (settings.showEvents) PermissionHint()
            } else if (settings.showEvents && !hasPerm && tier.index >= 1) {
                Spacer(GlanceModifier.height(D.s4))
                PermissionHint()
            }
        }
    }

    @Composable
    private fun Header(month: MonthData, tier: Tier) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = month.title,
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight().clickable(actionRunCallback<ResetMonthAction>()),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = tier.month.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = if (tier.nav) TextAlign.Start else TextAlign.Start,
                ),
            )
            if (tier.nav) {
                val ctx = LocalContext.current
                GhostNav(R.drawable.ic_chevron_left, ctx.getString(R.string.cal_prev_month), actionRunCallback<ShiftMonthAction>(shiftParams(-1)))
                Spacer(GlanceModifier.width(D.s2))
                GhostNav(R.drawable.ic_chevron_right, ctx.getString(R.string.cal_next_month), actionRunCallback<ShiftMonthAction>(shiftParams(1)))
            }
        }
    }

    /** Icon-only ghost button (no filled circle) — restrained, not chrome-heavy. */
    @Composable
    private fun GhostNav(icon: Int, desc: String, onClick: Action) {
        Box(
            modifier = GlanceModifier.size(32.dp).cornerRadius(16.dp).clickable(onClick),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(icon),
                contentDescription = desc,
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant),
                modifier = GlanceModifier.size(20.dp),
            )
        }
    }

    @Composable
    private fun WeekdayRow(month: MonthData, tier: Tier) {
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            month.weekdayHeaders.forEach { label ->
                Text(
                    text = label.uppercase(),
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight(),
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = tier.weekday.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                    ),
                )
            }
        }
    }

    @Composable
    private fun ColumnScope.Grid(month: MonthData, eventsByDay: Map<LocalDate, List<EventLite>>, tier: Tier) {
        Column(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
            month.weeks.forEach { week ->
                Row(
                    modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    week.forEach { cell -> DayCell(cell, eventsByDay[cell.date].orEmpty(), tier) }
                }
            }
        }
    }

    @Composable
    private fun RowScope.DayCell(cell: DayCell, events: List<EventLite>, tier: Tier) {
        Box(
            modifier = GlanceModifier
                .defaultWeight()
                .fillMaxHeight()
                .clickable(actionStartActivity(dayIntent(cell.date))),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                DayNumber(cell, tier)
                if (events.isNotEmpty() && tier.maxDots > 0) {
                    Spacer(GlanceModifier.height(D.s1))
                    DotRow(events, tier)
                }
            }
        }
    }

    /** Today = filled-primary disc (the single accent moment); never clips. */
    @Composable
    private fun DayNumber(cell: DayCell, tier: Tier) {
        if (cell.isToday) {
            Box(modifier = GlanceModifier.size(tier.disc.dp), contentAlignment = Alignment.Center) {
                // Oval drawable → a real circle on every API (cornerRadius only
                // clips on 31+); tinted to the live accent.
                Image(
                    provider = ImageProvider(R.drawable.today_disc),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
                    modifier = GlanceModifier.size(tier.disc.dp),
                )
                Text(
                    text = cell.day.toString(),
                    style = TextStyle(color = GlanceTheme.colors.onPrimary, fontSize = tier.day.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                )
            }
        } else {
            Text(
                text = cell.day.toString(),
                style = TextStyle(
                    color = if (cell.inCurrentMonth) GlanceTheme.colors.onSurface else GlanceTheme.colors.onSurfaceVariant,
                    fontSize = tier.day.sp,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center,
                ),
            )
        }
    }

    @Composable
    private fun DotRow(events: List<EventLite>, tier: Tier) {
        val fallback = GlanceTheme.colors.primary
        val colors = events.map { eventColor(it, fallback) }.distinct().take(tier.maxDots)
        Row(verticalAlignment = Alignment.CenterVertically) {
            colors.forEachIndexed { i, c ->
                if (i > 0) Spacer(GlanceModifier.width(D.s1))
                Box(modifier = GlanceModifier.size(tier.dot.dp).cornerRadius((tier.dot / 2).dp).background(c)) {}
            }
        }
    }

    private fun eventColor(e: EventLite, fallback: ColorProvider): ColorProvider =
        if (e.color != 0) ColorProvider(Color(e.color)) else fallback

    @Composable
    private fun Agenda(agenda: List<EventLite>) {
        val ctx = LocalContext.current
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            Eyebrow(ctx.getString(R.string.cal_upcoming), com.monthcalendar.widget.design.WPalettes.themed())
            Spacer(GlanceModifier.height(D.s3))
            if (agenda.isEmpty()) {
                Text(ctx.getString(R.string.cal_no_events), style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = D.label))
                return@Column
            }
            agenda.take(3).forEachIndexed { i, e ->
                if (i > 0) Spacer(GlanceModifier.height(D.s3))
                AgendaRow(e, ctx)
            }
        }
    }

    @Composable
    private fun AgendaRow(e: EventLite, ctx: Context) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .cornerRadius(D.rLg)
                .background(GlanceTheme.colors.secondaryContainer)
                .padding(horizontal = D.s6, vertical = D.s4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = GlanceModifier.size(8.dp).cornerRadius(4.dp).background(eventColor(e, GlanceTheme.colors.primary))) {}
            Spacer(GlanceModifier.width(D.s4))
            Text(
                text = e.title,
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(color = GlanceTheme.colors.onSecondaryContainer, fontSize = D.label, fontWeight = FontWeight.Medium),
            )
            Spacer(GlanceModifier.width(D.s4))
            Text(
                text = agendaWhen(e, LocalDate.now(), ctx),
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = D.caption),
            )
        }
    }

    @Composable
    private fun PermissionHint() {
        val ctx = LocalContext.current
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .cornerRadius(D.rLg)
                .background(GlanceTheme.colors.secondaryContainer)
                .padding(horizontal = D.s6, vertical = D.s5)
                .clickable(actionStartActivity(appIntent())),
        ) {
            Text(ctx.getString(R.string.cal_grant_events), style = TextStyle(color = GlanceTheme.colors.onSecondaryContainer, fontSize = D.label))
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

private fun agendaWhen(e: EventLite, today: LocalDate, ctx: Context): String {
    val d = e.date
    val dayLabel = when (d) {
        today -> ctx.getString(R.string.cal_today)
        today.plusDays(1) -> ctx.getString(R.string.cal_tomorrow)
        else -> "${d.dayOfMonth} ${CalendarModel.monthName(d.monthValue).take(3).lowercase()}"
    }
    if (e.allDay) return "$dayLabel · ${ctx.getString(R.string.cal_all_day).lowercase()}"
    val t = Instant.ofEpochMilli(e.begin).atZone(ZoneId.systemDefault())
    return "$dayLabel · %02d:%02d".format(t.hour, t.minute)
}
