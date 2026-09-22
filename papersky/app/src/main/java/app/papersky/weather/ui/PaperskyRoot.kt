package app.papersky.weather.ui

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import app.papersky.weather.AppContainer
import app.papersky.weather.R
import app.papersky.weather.core.model.MotionLevel
import app.papersky.weather.core.model.Place
import app.papersky.weather.core.model.UserSettings
import app.papersky.weather.core.model.momentAt
import app.papersky.weather.design.Haptics
import app.papersky.weather.design.LocalHaptics
import app.papersky.weather.design.LocalSceneClock
import app.papersky.weather.design.LocalScenePalette
import app.papersky.weather.design.PaperTheme
import app.papersky.weather.scene.Palettes
import app.papersky.weather.scene.SceneState
import app.papersky.weather.ui.home.HomeScreen
import app.papersky.weather.ui.home.HomeViewModel
import app.papersky.weather.ui.places.PlacesScreen
import app.papersky.weather.ui.places.PlacesViewModel
import app.papersky.weather.ui.scene.rememberSceneClock
import app.papersky.weather.ui.settings.SettingsScreen
import app.papersky.weather.ui.settings.SettingsViewModel
import app.papersky.weather.ui.widgets.WidgetStudioScreen
import app.papersky.weather.ui.widgets.WidgetStudioViewModel
import app.papersky.weather.widget.WidgetConfig
import app.papersky.weather.widget.WidgetDirectory
import app.papersky.weather.widget.config.Grid
import app.papersky.weather.widget.config.WidgetEditor
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable data object HomeRoute : NavKey
@Serializable data object PlacesRoute : NavKey
@Serializable data object SettingsRoute : NavKey
@Serializable data object WidgetsRoute : NavKey
@Serializable data class WidgetEditRoute(val appWidgetId: Int) : NavKey

fun appViewModels(c: AppContainer, context: Context) = viewModelFactory {
    initializer { HomeViewModel(c, context.applicationContext as android.app.Application) }
    initializer { PlacesViewModel(c) }
    initializer { SettingsViewModel(c) }
    initializer { WidgetStudioViewModel(c, context.applicationContext as android.app.Application) }
}

/** Haptics, the animation clock and system-bar contrast shared by every Papersky window. */
@Composable
fun PaperskyChrome(settings: UserSettings, scene: SceneState, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val haptics = remember { Haptics(context) }
    haptics.enabled = settings.haptics
    haptics.strength = settings.hapticStrength
    val clock = rememberSceneClock(
        when (settings.motion) {
            MotionLevel.Full -> 1f
            MotionLevel.Gentle -> 0.45f
            MotionLevel.Still -> 0f
        },
    )
    val palette = remember(scene) { Palettes.forState(scene) }
    val activity = LocalActivity.current as? ComponentActivity
    val darkSky = palette.isDarkSky
    LaunchedEffect(darkSky, activity) {
        val style = if (darkSky) SystemBarStyle.dark(AndroidColor.TRANSPARENT) else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        activity?.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
    CompositionLocalProvider(LocalHaptics provides haptics, LocalSceneClock provides clock) {
        PaperTheme(palette) {
            CompositionLocalProvider(LocalScenePalette provides palette, content = content)
        }
    }
}

@Composable
fun PaperskyRoot(container: AppContainer, pendingPlace: StateFlow<String?>, onPlaceHandled: () -> Unit) {
    val context = LocalContext.current
    val factory = remember { appViewModels(container, context) }
    val home: HomeViewModel = viewModel(factory = factory)
    val state by home.state.collectAsStateWithLifecycle()
    val pending by pendingPlace.collectAsStateWithLifecycle()
    LaunchedEffect(pending) {
        pending?.let {
            home.select(it)
            onPlaceHandled()
        }
    }
    val scene = remember(state.forecast, state.place) {
        state.forecast?.let { f -> f.momentAt(System.currentTimeMillis() / 1000)?.let { SceneState.from(it, SceneState.seedFor(f.placeId), f) } }
            ?: SceneState(seed = 3)
    }
    val units = state.settings.resolvedUnits(androidx.compose.ui.platform.LocalConfiguration.current.locales[0])
    val motion = state.settings.motion

    PaperskyChrome(state.settings, scene) {
        val backStack = rememberNavBackStack(HomeRoute)
        val sheet = spring<androidx.compose.ui.unit.IntOffset>(dampingRatio = 0.88f, stiffness = 360f)
        val settle = spring<Float>(dampingRatio = 0.9f, stiffness = 360f)
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
            // A new sheet of paper slides over; the one beneath sinks back and dims.
            transitionSpec = {
                (slideInVertically(sheet) { it / 4 } + fadeIn(tween(220)) + scaleIn(settle, initialScale = 0.985f)) togetherWith
                    (scaleOut(tween(420), targetScale = 0.93f) + fadeOut(tween(420), targetAlpha = 0.3f))
            },
            popTransitionSpec = {
                (scaleIn(tween(380), initialScale = 0.93f) + fadeIn(tween(320), initialAlpha = 0.3f)) togetherWith
                    (slideOutVertically(spring(dampingRatio = 1f, stiffness = 420f)) { it / 3 } + fadeOut(tween(240)))
            },
            predictivePopTransitionSpec = {
                (scaleIn(initialScale = 0.92f) + fadeIn(initialAlpha = 0.35f)) togetherWith (scaleOut(targetScale = 0.9f) + fadeOut())
            },
            entryProvider = entryProvider {
                entry<HomeRoute> {
                    HomeScreen(
                        vm = home,
                        onOpenPlaces = { backStack.add(PlacesRoute) },
                        onOpenSettings = { backStack.add(SettingsRoute) },
                        onOpenWidgets = { backStack.add(WidgetsRoute) },
                    )
                }
                entry<PlacesRoute> {
                    val vm: PlacesViewModel = viewModel(factory = factory)
                    PlacesScreen(vm, scene, units, motion) { backStack.removeLastOrNull() }
                }
                entry<SettingsRoute> {
                    val vm: SettingsViewModel = viewModel(factory = factory)
                    SettingsScreen(vm, scene) { backStack.removeLastOrNull() }
                }
                entry<WidgetsRoute> {
                    val vm: WidgetStudioViewModel = viewModel(factory = factory)
                    WidgetStudioScreen(vm, scene, motion, onEdit = { backStack.add(WidgetEditRoute(it)) }) { backStack.removeLastOrNull() }
                }
                entry<WidgetEditRoute> { key ->
                    WidgetEditScreen(container, key.appWidgetId, stringResource(R.string.editor_save)) { backStack.removeLastOrNull() }
                }
            },
        )
    }
}

/** Editor bound to a placed widget: loads its config and writes it back. */
@Composable
fun WidgetEditScreen(container: AppContainer, appWidgetId: Int, doneLabel: String, onFinished: (saved: Boolean) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val places by container.places.all.collectAsStateWithLifecycle(emptyList())
    val loaded by produceState<Pair<WidgetConfig, DpSize>?>(null, appWidgetId) {
        val manager = GlanceAppWidgetManager(context)
        val id = runCatching { manager.getGlanceIdBy(appWidgetId) }.getOrNull()
        val config = id?.let { WidgetDirectory.readConfig(context, it) } ?: WidgetConfig()
        val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId)
        val w = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0)
        val h = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
        val size = if (w > 0 && h > 0) DpSize(w.dp, h.dp) else Grid.size(4, 2)
        value = config to size
    }
    val pair = loaded ?: return
    WidgetEditor(
        initial = pair.first,
        places = places,
        initialSize = pair.second,
        title = stringResource(R.string.editor_title),
        doneLabel = doneLabel,
        onDone = { config ->
            scope.launch {
                runCatching {
                    val id = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
                    WidgetDirectory.writeConfig(context, id, config)
                }
                onFinished(true)
            }
        },
        onClose = { onFinished(false) },
    )
}

/** Place-less default used by the config activity before anything was downloaded. */
fun defaultPlaceId(places: List<Place>): String = places.firstOrNull()?.id ?: Place.HERE
