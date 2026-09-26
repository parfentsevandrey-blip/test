package app.rosa.weather.ui.common

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import app.rosa.weather.R
import app.rosa.weather.core.designsystem.component.GlassTab
import app.rosa.weather.core.designsystem.component.GlassTabBar
import app.rosa.weather.core.designsystem.component.GlassTabBarHeight
import app.rosa.weather.core.designsystem.component.RosaIcon
import app.rosa.weather.core.designsystem.component.SkyScrollEdge
import app.rosa.weather.core.designsystem.motion.RosaMotion
import app.rosa.weather.navigation.Home

/** The app's four places, a tab each in the bar at the bottom of the screen. */
enum class RosaTab(val key: NavKey, val icon: RosaIcon, @StringRes val label: Int) {
    Weather(Home, RosaIcon.Weather, R.string.tab_weather),
    Places(app.rosa.weather.navigation.Places, RosaIcon.Pin, R.string.places_title),
    Widgets(app.rosa.weather.navigation.Widgets, RosaIcon.Widgets, R.string.widgets_title),
    Settings(app.rosa.weather.navigation.Settings, RosaIcon.Settings, R.string.settings_title),
    ;

    companion object {
        /** The tab whose screen [key] is, or null for a screen pushed over a tab. */
        fun of(key: NavKey?): RosaTab? = entries.firstOrNull { it.key == key }
    }
}

/** The room the tab bar takes at the bottom of the screen, where it shows (else nothing). */
val LocalTabBarInset = staticCompositionLocalOf { 0.dp }

/**
 * The bottom padding scrolling content needs to end in view: clear of the tab bar where it shows,
 * else of the system's navigation bar.
 */
@Composable
fun bottomContentInset(): Dp {
    val bar = LocalTabBarInset.current
    return if (bar > 0.dp) bar else WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
}

/**
 * The screens, with the tab bar floating over their bottom edge where the thumb reaches: content
 * scrolls to just above it and fades into the sky before it reaches the glass. A screen pushed
 * over a tab ([selected] null) has the bar slide away.
 */
@Composable
fun TabBarScaffold(selected: RosaTab?, onSelect: (RosaTab) -> Unit, content: @Composable () -> Unit) {
    val navigation = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // While the bar slides away it keeps showing the tab it had.
    var last by remember { mutableStateOf(selected ?: RosaTab.Weather) }
    SideEffect { if (selected != null) last = selected }
    val shown = selected ?: last
    // Room for the bar and for the band of clear sky above it, so the last card can scroll into
    // full view above the fade.
    val inset = if (selected != null) navigation + BarGap + GlassTabBarHeight + ClearAbove else 0.dp
    val tabs = RosaTab.entries.map { GlassTab(it.icon, stringResource(it.label)) }
    CompositionLocalProvider(LocalTabBarInset provides inset) {
        Box(Modifier.fillMaxSize()) {
            content()
            AnimatedVisibility(
                visible = selected != null,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(RosaMotion.gelOffset) { it } + fadeIn(),
                exit = slideOutVertically(RosaMotion.gelOffset) { it } + fadeOut(),
            ) {
                Box(contentAlignment = Alignment.BottomCenter) {
                    // The bar floats in a band of clear sky: whatever scrolls down toward it has
                    // faded out before it gets there, so the two never run together.
                    SkyScrollEdge(
                        height = navigation + BarGap + GlassTabBarHeight + ClearAbove + 10.dp,
                        solid = navigation + BarGap + GlassTabBarHeight + 6.dp,
                        atBottom = true,
                    )
                    GlassTabBar(
                        tabs = tabs,
                        selected = shown.ordinal,
                        onSelect = { onSelect(RosaTab.entries[it]) },
                        modifier = Modifier.navigationBarsPadding().padding(start = 18.dp, end = 18.dp, bottom = BarGap),
                    )
                }
            }
        }
    }
}

/** Between the tab bar and the bottom of the screen (or the system's navigation bar). */
private val BarGap = 10.dp

/** The clear sky kept above the bar, where content fades out. */
private val ClearAbove = 40.dp
