package app.rosa.calendar.ui.month

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.rosa.calendar.R
import app.rosa.calendar.ui.CalendarViewModel
import app.rosa.calendar.ui.LocalTabBarInset
import app.rosa.weather.core.designsystem.component.GlassButton
import app.rosa.weather.core.designsystem.component.GlassIconButton
import app.rosa.weather.core.designsystem.component.GlassSurface
import app.rosa.weather.core.designsystem.component.RosaIcon
import app.rosa.weather.core.designsystem.glass.GlassStyle
import app.rosa.weather.core.designsystem.haptics.LocalHaptics
import app.rosa.weather.core.designsystem.motion.RosaMotion
import app.rosa.weather.core.designsystem.theme.Rosa
import app.rosa.weather.core.model.CalendarMonth
import app.rosa.weather.widget.render.calendar.SeasonClock
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * The month: its name over the week's painting, its days on a pane of frosted glass that pages
 * sideways to the months around it, and the chosen day below — its weather and its events. A day
 * is chosen with a tap, or by holding it and sliding: a lens follows the finger across the days.
 */
@Composable
fun MonthScreen(
    viewModel: CalendarViewModel,
    month: YearMonth,
    today: LocalDate,
    selected: LocalDate,
    onMonth: (YearMonth) -> Unit,
    onSelect: (LocalDate) -> Unit,
) {
    val settings = viewModel.settings.collectAsStateWithLifecycle().value ?: return
    val events by viewModel.monthEvents.collectAsStateWithLifecycle()
    val weather by viewModel.weatherState.collectAsStateWithLifecycle()
    val allowed by viewModel.eventsAllowed.collectAsStateWithLifecycle()
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    val locale = Locale.getDefault()
    val firstDay = CalendarMonth.firstDayFor(settings.weekStart, locale)

    // Page CENTER is the month the screen opened on; the others lie either side of it.
    val base = remember { month }
    val pager = rememberPagerState(initialPage = CENTER + monthsBetween(base, month)) { PAGES }
    val onMonthNow by rememberUpdatedState(onMonth)
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { page -> onMonthNow(base.plusMonths((page - CENTER).toLong())) }
    }
    LaunchedEffect(month) {
        val target = CENTER + monthsBetween(base, month)
        if (pager.currentPage != target && !pager.isScrollInProgress) pager.animateScrollToPage(target)
    }
    // The name follows the pages as they turn, before they settle.
    val onScreen = base.plusMonths((pager.currentPage - CENTER).toLong())
    fun go(to: YearMonth) {
        haptics?.tick()
        scope.launch { pager.animateScrollToPage(CENTER + monthsBetween(base, to)) }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.resumed() }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = LocalTabBarInset.current),
    ) {
        MonthHeader(
            month = onScreen,
            today = today,
            onPrevious = { go(onScreen.minusMonths(1)) },
            onNext = { go(onScreen.plusMonths(1)) },
            onToday = {
                go(YearMonth.from(today))
                onSelect(today)
            },
        )
        GlassSurface(
            Modifier.padding(horizontal = 12.dp).fillMaxWidth(),
            style = GlassStyle.Frosted,
            cornerRadius = 30.dp,
            touchResponsive = false,
        ) {
            HorizontalPager(pager, Modifier.fillMaxWidth(), beyondViewportPageCount = 1, key = { it }) { page ->
                val pageMonth = base.plusMonths((page - CENTER).toLong())
                MonthGrid(
                    month = pageMonth,
                    today = today,
                    selected = selected,
                    firstDay = firstDay,
                    weekNumbers = settings.weekNumbers,
                    events = events,
                    weather = weather,
                    onSelect = { date ->
                        onSelect(date)
                        val target = YearMonth.from(date)
                        if (target != pageMonth) go(target)
                    },
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        DayCard(
            date = selected,
            today = today,
            events = events[selected].orEmpty(),
            weather = weather,
            showEvents = settings.showEvents,
            eventsAllowed = allowed,
            onAllowEvents = { permission.launch(Manifest.permission.READ_CALENDAR) },
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}

/** The month's name, the year and the week's painting by name, and the way to the months around. */
@Composable
private fun MonthHeader(
    month: YearMonth,
    today: LocalDate,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    val context = LocalContext.current
    val locale = Locale.getDefault()
    val week = SeasonClock.weekFor(month, today)
    val scenes = remember { context.resources.getStringArray(app.rosa.weather.widget.R.array.calendar_weeks) }
    val scene = scenes.getOrNull(week - 1)
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 22.dp, end = 14.dp, top = 12.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            AnimatedContent(
                month,
                transitionSpec = {
                    val forward = targetState > initialState
                    (slideInHorizontally(RosaMotion.gelOffset) { if (forward) it / 4 else -it / 4 } + fadeIn(tween(220)) + scaleIn(RosaMotion.gel(), initialScale = 0.96f)) togetherWith
                        (slideOutHorizontally(RosaMotion.gelOffset) { if (forward) -it / 4 else it / 4 } + fadeOut(tween(140)) + scaleOut(targetScale = 0.96f))
                },
                label = "month",
            ) { shown ->
                Column {
                    Text(
                        shown.month.getDisplayName(TextStyle.FULL_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) },
                        style = Rosa.type.display.copy(fontSize = Rosa.type.display.fontSize * 1.12f),
                        color = Rosa.colors.ink,
                        maxLines = 1,
                        modifier = Modifier.semantics { heading() },
                    )
                    val line = listOfNotNull(shown.year.toString(), scene).joinToString(" · ")
                    Text(line, style = Rosa.type.label, color = Rosa.colors.inkSoft, maxLines = 1)
                }
            }
        }
        AnimatedVisibility(
            visible = YearMonth.from(today) != month,
            enter = fadeIn() + scaleIn(RosaMotion.gel(), initialScale = 0.6f),
            exit = fadeOut() + scaleOut(targetScale = 0.6f),
        ) {
            GlassButton(onClick = onToday, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
                Text(stringResource(R.string.month_today), style = Rosa.type.label, color = Rosa.colors.ink)
            }
        }
        Spacer(Modifier.width(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassIconButton(RosaIcon.Back, stringResource(R.string.calendar_previous), onPrevious)
            GlassIconButton(RosaIcon.Chevron, stringResource(R.string.calendar_next), onNext)
        }
    }
}

private const val PAGES = 2401
private const val CENTER = PAGES / 2

private fun monthsBetween(from: YearMonth, to: YearMonth): Int = ChronoUnit.MONTHS.between(from, to).toInt().coerceIn(-CENTER, CENTER)
