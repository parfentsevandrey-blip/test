package app.rosa.calendar.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.rosa.calendar.R
import app.rosa.calendar.data.PaintingLight
import app.rosa.calendar.ui.events.EventsScreen
import app.rosa.calendar.ui.month.MonthScreen
import app.rosa.calendar.ui.settings.SettingsScreen
import app.rosa.calendar.ui.widget.WidgetScreen
import app.rosa.weather.core.designsystem.component.GlassTab
import app.rosa.weather.core.designsystem.component.GlassTabBar
import app.rosa.weather.core.designsystem.component.GlassTabBarHeight
import app.rosa.weather.core.designsystem.component.RosaEnvironment
import app.rosa.weather.core.designsystem.component.RosaIcon
import app.rosa.weather.core.designsystem.component.SkyScrollEdge
import app.rosa.weather.core.designsystem.motion.RosaMotion
import app.rosa.weather.widget.render.calendar.SeasonClock
import app.rosa.weather.widget.render.calendar.WeekArt
import java.time.LocalDate
import java.time.YearMonth

/** The app's four places, a tab each in the glass bar at the bottom of the screen. */
enum class CalendarTab(val icon: RosaIcon, @StringRes val label: Int) {
    Month(RosaIcon.Calendar, R.string.tab_month),
    Events(RosaIcon.Agenda, R.string.tab_events),
    Widget(RosaIcon.Widgets, R.string.tab_widget),
    Settings(RosaIcon.Settings, R.string.tab_settings),
}

/** The room the tab bar takes at the bottom of the screen: scrolling content ends clear of it. */
val LocalTabBarInset = staticCompositionLocalOf { 0.dp }

/**
 * One painting behind the whole app — the week's, as on the widget — and the screens as glass
 * over it: the month, the events ahead, the widget and the settings, a tab each. The painting is
 * the browsed month's on the month, today's elsewhere, and the whole palette follows it.
 */
@Composable
fun CalendarAppRoot(viewModel: CalendarViewModel = hiltViewModel()) {
    val settings = viewModel.settings.collectAsStateWithLifecycle().value ?: return
    val today by viewModel.today.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(CalendarTab.Month) }
    var monthIndex by rememberSaveable { mutableLongStateOf(YearMonth.from(today).index) }
    var selectedDay by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    val month = monthOf(monthIndex)
    val selected = LocalDate.ofEpochDay(selectedDay)
    LaunchedEffect(month) { viewModel.show(month) }
    LifecycleResumeEffect(Unit) {
        viewModel.resumed()
        onPauseOrDispose { }
    }
    BackHandler(enabled = tab != CalendarTab.Month) { tab = CalendarTab.Month }

    val week = SeasonClock.weekFor(if (tab == CalendarTab.Month) month else YearMonth.from(today), today)
    val art = WeekArt.of(week)
    val systemNight = isSystemInDarkTheme()
    val night = when (settings.light) {
        PaintingLight.Auto -> systemNight
        PaintingLight.Day -> false
        PaintingLight.Night -> true
    }
    val palette = remember(week, night) { PaintingPalette.of(art, night) }
    SystemBarsFollow(palette.isLight)

    RosaEnvironment(settings.asAppSettings(), palette) {
        PaintingBackdrop(art, night, live = settings.livePainting) {
            TabScaffold(tab, onSelect = { tab = it }) {
                AnimatedContent(tab, transitionSpec = { tabSwitch() }, label = "tab") { current ->
                    when (current) {
                        CalendarTab.Month -> MonthScreen(
                            viewModel = viewModel,
                            month = month,
                            today = today,
                            selected = selected,
                            onMonth = { monthIndex = it.index },
                            onSelect = { selectedDay = it.toEpochDay() },
                        )
                        CalendarTab.Events -> EventsScreen(viewModel, today) { date ->
                            selectedDay = date.toEpochDay()
                            monthIndex = YearMonth.from(date).index
                            tab = CalendarTab.Month
                        }
                        CalendarTab.Widget -> WidgetScreen()
                        CalendarTab.Settings -> SettingsScreen(viewModel)
                    }
                }
            }
        }
    }
}

/** Months counted from year 0, so the month on show survives recreation as a number. */
private val YearMonth.index: Long get() = year * 12L + (monthValue - 1)

private fun monthOf(index: Long): YearMonth = YearMonth.of((index / 12).toInt(), (index % 12).toInt() + 1)

/** One tab gives way to another in place: a quick cross-fade, the new one settling as it arrives. */
private fun tabSwitch(): ContentTransform =
    (fadeIn(tween(220)) + scaleIn(RosaMotion.gel(), initialScale = 0.98f)) togetherWith fadeOut(tween(150))

/**
 * The screens, with the tab bar floating over their bottom edge where the thumb reaches: content
 * scrolls to just above it and fades into the painting before it reaches the glass.
 */
@Composable
private fun TabScaffold(selected: CalendarTab, onSelect: (CalendarTab) -> Unit, content: @Composable () -> Unit) {
    val navigation = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val inset = navigation + BarGap + GlassTabBarHeight + ClearAbove
    val tabs = CalendarTab.entries.map { GlassTab(it.icon, stringResource(it.label)) }
    CompositionLocalProvider(LocalTabBarInset provides inset) {
        Box(Modifier.fillMaxSize()) {
            content()
            Box(Modifier.align(Alignment.BottomCenter), contentAlignment = Alignment.BottomCenter) {
                SkyScrollEdge(
                    height = navigation + BarGap + GlassTabBarHeight + ClearAbove + 10.dp,
                    solid = navigation + BarGap + GlassTabBarHeight + 6.dp,
                    atBottom = true,
                )
                GlassTabBar(
                    tabs = tabs,
                    selected = selected.ordinal,
                    onSelect = { onSelect(CalendarTab.entries[it]) },
                    modifier = Modifier.navigationBarsPadding().padding(start = 18.dp, end = 18.dp, bottom = BarGap),
                )
            }
        }
    }
}

/** Between the tab bar and the bottom of the screen (or the system's navigation bar). */
private val BarGap: Dp = 10.dp

/** The clear painting kept above the bar, where content fades out. */
private val ClearAbove: Dp = 40.dp

/** The clock, battery and navigation icons follow the painting: dark over a light one, light over a dark one. */
@Composable
private fun SystemBarsFollow(light: Boolean) {
    val view = LocalView.current
    LaunchedEffect(view, light) {
        val window = view.context.findActivity()?.window ?: return@LaunchedEffect
        val bars = WindowCompat.getInsetsController(window, view)
        bars.isAppearanceLightStatusBars = light
        bars.isAppearanceLightNavigationBars = light
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
