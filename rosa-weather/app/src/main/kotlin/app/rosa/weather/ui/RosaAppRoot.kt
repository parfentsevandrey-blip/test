package app.rosa.weather.ui

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import app.rosa.weather.core.designsystem.component.RosaEnvironment
import app.rosa.weather.core.designsystem.component.SkyBackdrop
import app.rosa.weather.core.designsystem.motion.RosaMotion
import app.rosa.weather.navigation.Home
import app.rosa.weather.navigation.Places
import app.rosa.weather.navigation.Search
import app.rosa.weather.navigation.Settings
import app.rosa.weather.navigation.Widgets
import app.rosa.weather.ui.common.LocalSky
import app.rosa.weather.ui.common.SkyController
import app.rosa.weather.ui.home.HomeRoute
import app.rosa.weather.ui.home.HomeViewModel
import app.rosa.weather.ui.places.PlacesRoute
import app.rosa.weather.ui.search.SearchRoute
import app.rosa.weather.ui.settings.SettingsRoute
import app.rosa.weather.ui.settings.SettingsViewModel

/**
 * One living sky behind the whole app; screens are glass that slides over it. Navigation 3 with
 * predictive back: pages float away while the sky stays put.
 */
@Composable
fun RosaAppRoot() {
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val sky = remember { SkyController(SkyController.placeholder(System.currentTimeMillis() / 1000)) }
    val backStack = rememberNavBackStack(Home)

    CompositionLocalProvider(LocalSky provides sky) {
        RosaEnvironment(settings, sky.palette) {
            SkyBackdrop(sky.params, settings.effects, stage = sky.stage, transitionMillis = sky.transitionMillis) {
                NavDisplay(
                    backStack = backStack,
                    onBack = { backStack.removeLastOrNull() },
                    entryDecorators = listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                    transitionSpec = { glassPush() },
                    popTransitionSpec = { glassPop() },
                    predictivePopTransitionSpec = { glassPop() },
                    entryProvider = entryProvider {
                        entry<Home> {
                            val vm: HomeViewModel = hiltViewModel()
                            HomeRoute(
                                viewModel = vm,
                                onOpenPlaces = { backStack.add(Places) },
                                onOpenSettings = { backStack.add(Settings) },
                                onOpenWidgets = { backStack.add(Widgets) },
                                onOpenSearch = { backStack.add(Search) },
                            )
                        }
                        entry<Places> {
                            PlacesRoute(hiltViewModel(), onBack = { backStack.removeLastOrNull() }, onAdd = { backStack.add(Search) })
                        }
                        entry<Search> {
                            SearchRoute(hiltViewModel(), onBack = { backStack.removeLastOrNull() }, onAdded = {
                                while (backStack.size > 1) backStack.removeLastOrNull()
                            })
                        }
                        entry<Settings> { SettingsRoute(settingsViewModel, onBack = { backStack.removeLastOrNull() }) }
                        entry<Widgets> { app.rosa.weather.ui.widgets.WidgetsRoute(hiltViewModel(), onBack = { backStack.removeLastOrNull() }) }
                    },
                )
            }
        }
    }
}

private fun glassPush(): ContentTransform =
    (slideInHorizontally(RosaMotion.gelOffset) { it / 3 } + fadeIn(tween(260)) + scaleIn(RosaMotion.gel(), initialScale = 0.96f)) togetherWith
        (fadeOut(tween(200)) + scaleOut(RosaMotion.gel(), targetScale = 0.94f))

private fun glassPop(): ContentTransform =
    (fadeIn(tween(260)) + scaleIn(RosaMotion.gel(), initialScale = 0.94f)) togetherWith
        (slideOutHorizontally(RosaMotion.gelOffset) { it / 3 } + fadeOut(tween(200)) + scaleOut(RosaMotion.gel(), targetScale = 0.96f))
