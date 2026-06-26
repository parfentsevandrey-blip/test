package com.example.calendarwidget.widget

import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.calendarwidget.data.CalendarMath
import com.example.calendarwidget.data.CalendarRepository
import com.example.calendarwidget.data.SettingsRepository
import com.example.calendarwidget.data.WidgetSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Adaptive month-view calendar widget (Jetpack Glance).
 *
 * One widget, three responsive size buckets (раздел 5):
 *  - TINY  2×2  → mini grid, short month title, no nav/agenda
 *  - WIDE  4×2  → full grid + month navigation
 *  - LARGE 4×4  → grid + agenda for today
 */
class MonthWidget : GlanceAppWidget() {

    companion object {
        val TINY = DpSize(160.dp, 160.dp)   // 2×2
        val WIDE = DpSize(320.dp, 160.dp)   // 4×2
        val LARGE = DpSize(320.dp, 320.dp)  // 4×4
    }

    override val sizeMode = SizeMode.Responsive(setOf(TINY, WIDE, LARGE))
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val settings = SettingsRepository(context).read()
        val (year, month) = readDisplayedMonth(context, id)
        val events = withContext(Dispatchers.IO) {
            CalendarRepository(context).eventsForMonth(year, month)
        }

        provideContent {
            val ctx = LocalContext.current
            val dark = ctx.isSystemDark()
            val palette = WidgetPalette(settings, dark)
            val size = LocalSize.current

            val compact = size == TINY
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(palette.provider(palette.glass))
                    .cornerRadius(settings.radius.dp)
                    .padding(if (compact) 11.dp else 16.dp),
            ) {
                when (size) {
                    LARGE -> LargeLayout(settings, palette, year, month, events)
                    WIDE -> StandardLayout(settings, palette, year, month, events, compact = false, showNav = true)
                    else -> StandardLayout(settings, palette, year, month, events, compact = true, showNav = false)
                }
            }
        }
    }
}

private fun Context.isSystemDark(): Boolean {
    val mode = resources.configuration.uiMode and
        android.content.res.Configuration.UI_MODE_NIGHT_MASK
    return mode == android.content.res.Configuration.UI_MODE_NIGHT_YES
}

/** TINY (compact) and WIDE buckets: header + grid, no agenda. */
@androidx.compose.runtime.Composable
private fun StandardLayout(
    settings: WidgetSettings,
    palette: WidgetPalette,
    year: Int,
    month: Int,
    events: Map<Int, List<com.example.calendarwidget.data.CalendarEvent>>,
    compact: Boolean,
    showNav: Boolean,
) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        WidgetHeader(settings, palette, year, month, compact = compact, showNav = showNav)
        Spacer(GlanceModifier.height(if (compact) 7.dp else 10.dp))
        MonthGrid(
            settings = settings,
            palette = palette,
            year = year,
            month = month,
            events = events,
            compact = compact,
            modifier = GlanceModifier.fillMaxSize(),
        )
    }
}

/** LARGE bucket: header + grid + agenda for today (when the current month is shown). */
@androidx.compose.runtime.Composable
private fun LargeLayout(
    settings: WidgetSettings,
    palette: WidgetPalette,
    year: Int,
    month: Int,
    events: Map<Int, List<com.example.calendarwidget.data.CalendarEvent>>,
) {
    val today = CalendarMath.today()
    val currentMonthShown = today.year == year && today.month == month
    Column(modifier = GlanceModifier.fillMaxSize()) {
        WidgetHeader(settings, palette, year, month, compact = false, showNav = true)
        Spacer(GlanceModifier.height(10.dp))
        MonthGrid(
            settings = settings,
            palette = palette,
            year = year,
            month = month,
            events = events,
            compact = false,
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
        )
        if (settings.showAgenda && currentMonthShown) {
            AgendaList(
                settings = settings,
                palette = palette,
                month = month,
                day = today.day,
                events = events[today.day].orEmpty(),
            )
        }
    }
}

@androidx.compose.runtime.Composable
private fun WidgetHeader(
    settings: WidgetSettings,
    palette: WidgetPalette,
    year: Int,
    month: Int,
    compact: Boolean,
    showNav: Boolean,
) {
    val title = if (compact) {
        CalendarMath.MONTHS_SHORT[month]
    } else {
        "${CalendarMath.MONTHS[month]} $year"
    }
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = TextStyle(
                color = palette.provider(palette.text),
                fontSize = (if (compact) 13 else 17).sp * settings.fontScale,
                fontWeight = FontWeight.Bold,
            ),
        )
        Spacer(GlanceModifier.defaultWeight())
        if (showNav) {
            NavButton("‹", palette, delta = -1)
            Spacer(GlanceModifier.width(6.dp))
            NavButton("›", palette, delta = 1)
        }
    }
}

@androidx.compose.runtime.Composable
private fun NavButton(glyph: String, palette: WidgetPalette, delta: Int) {
    Box(
        modifier = GlanceModifier
            .size(26.dp)
            .background(palette.provider(palette.navBg))
            .cornerRadius(13.dp)
            .clickable(
                actionRunCallback<ChangeMonthAction>(
                    actionParametersOf(ChangeMonthAction.DELTA to delta),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            style = TextStyle(
                color = palette.provider(palette.muted),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}
