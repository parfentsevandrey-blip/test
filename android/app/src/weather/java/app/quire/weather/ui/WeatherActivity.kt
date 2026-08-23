package app.quire.weather.ui

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EditLocation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import app.quire.R
import app.quire.calendar.m3.QuireTheme
import app.quire.weather.WeatherWidgetProvider

/**
 * The weather app.
 *
 * It is its own application rather than a screen inside the calendar: separate on the launcher,
 * separately installable, and — the part that matters — separately permissioned. A calendar that
 * carries a location permission because it happens to also show the weather is asking for
 * something it does not need, and this is the only arrangement in which it does not.
 *
 * What the two share is a source tree, not a process: the palette, the Material theme and the card
 * surface are the same code, so the two widgets sitting next to each other on a home screen are
 * plainly the same object at two jobs.
 */
class WeatherActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { WeatherApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun WeatherApp(model: WeatherModel = viewModel()) {
    val haptics = LocalHapticFeedback.current

    var choosing by rememberSaveable { mutableStateOf(false) }
    var configuring by rememberSaveable { mutableStateOf(false) }

    val requestLocation = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) model.permissionGranted() }

    // Alerts are the only thing here that needs to be allowed to interrupt, so the ask happens
    // when they are switched on rather than at launch.
    val requestNotifications = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { model.notificationsAnswered() }

    LaunchedEffect(Unit) {
        model.refresh()
        // Re-armed on every open. The job is scheduled when a widget is placed and when the
        // package updates, but a job can be lost — data cleared, forced stop — and the app being
        // opened is the one signal that always still arrives.
        app.quire.weather.WeatherRefresh.schedule(model.getApplication())
    }

    // Expressive on purpose, and now the only one of the three that asks for it: this is the
    // app with the sky and the hero in it, so it is where the loud scheme is earned.
    QuireTheme(
        dark = isSystemInDarkTheme(),
        dynamic = true,
        motion = androidx.compose.material3.MotionScheme.expressive(),
    ) {
        val scrollBehavior =
            TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
        val refresh = rememberPullToRefreshState()

        Scaffold(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                LargeFlexibleTopAppBar(
                    // The place is the title, because it is the one word that changes what every
                    // number on the page means; the app's own name is on the launcher, where names
                    // belong. The date rides under it — a weather page is read in the morning, and
                    // "what day is it" is the second question of a morning.
                    title = {
                        // The place rolls in the way the calendar's months do — up, which reads
                        // as "new" and is the truth about a place you just chose.
                        app.quire.calendar.m3.RollingLabel(
                            text = if (configuring) {
                                stringResource(R.string.wx_settings)
                            } else {
                                model.forecast?.place?.takeIf { it.isNotBlank() }
                                    ?: stringResource(R.string.weather)
                            },
                            order = 0,
                        )
                    },
                    subtitle = {
                        if (!configuring) {
                            val locale = app.quire.calendar.m3.rememberLocale()
                            val today = java.time.LocalDate.now().format(
                                java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM", locale),
                            ).replaceFirstChar { it.titlecase(locale) }
                            Text(today)
                        }
                    },
                    navigationIcon = {
                        if (configuring) {
                            IconButton(onClick = { configuring = false }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                            }
                        }
                    },
                    actions = {
                        if (!configuring) {
                            IconButton(onClick = { choosing = true }) {
                                Icon(
                                    imageVector = Icons.Default.EditLocation,
                                    contentDescription = stringResource(R.string.wx_place),
                                )
                            }
                        }
                        IconButton(onClick = { configuring = !configuring }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = stringResource(R.string.wx_settings),
                            )
                        }
                    },
                    // Transparent over the forecast *at rest*, so the sky behind the page runs up
                    // behind the title instead of stopping dead at the bottom of the bar. An
                    // opaque bar over a coloured page draws a hard horizontal line across the
                    // screen with two square corners on it, which is the one edge on the whole
                    // screen that nothing else has.
                    //
                    // But only at rest. `scrolledContainerColor` exists precisely so a bar can
                    // take a ground once the page passes under it, and setting it transparent
                    // too — which is what shipped — meant the page never stopped showing
                    // through: a real phone printed "Ближайшие 24 часа" straight across
                    // "Москва". Material cross-fades between the two as the bar collapses, so
                    // the sky keeps its clean top edge and the title keeps its legibility, and
                    // neither costs the other anything.
                    //
                    // The settings screen has no sky and keeps the usual bar.
                    colors = if (configuring) {
                        TopAppBarDefaults.topAppBarColors()
                    } else {
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        )
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { padding ->
            // The two screens trade places rather than snapping: settings arrives from the side
            // its gear lives on and the forecast comes back from the other, each over a fade —
            // the standard grammar for "you went somewhere" as opposed to "the page changed".
            // Read outside the spec lambda, which cannot reach the theme once it is running.
            val travel = MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntOffset>()
            val appear = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
            val leave = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
            AnimatedContent(
                targetState = configuring,
                transitionSpec = {
                    val fromEnd = targetState
                    (
                        slideInHorizontally(travel) { full -> if (fromEnd) full / 4 else -full / 4 } +
                            fadeIn(appear)
                        ) togetherWith (
                        slideOutHorizontally(travel) { full -> if (fromEnd) -full / 4 else full / 4 } +
                            fadeOut(leave)
                        )
                },
                label = "screens",
            ) { inSettings ->
                if (inSettings) {
                    WeatherSettingsScreen(model, padding)
                } else {
                    PullToRefreshBox(
                        isRefreshing = model.loading,
                        onRefresh = {
                            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                            model.refresh(force = true)
                            WeatherWidgetProvider.requestUpdate(model.getApplication())
                        },
                        state = refresh,
                        modifier = Modifier.fillMaxSize(),
                        indicator = {
                            PullToRefreshDefaults.LoadingIndicator(
                                state = refresh,
                                isRefreshing = model.loading,
                                modifier = Modifier.align(Alignment.TopCenter),
                            )
                        },
                    ) {
                        WeatherScreen(
                            model = model,
                            padding = padding,
                            onGrant = {
                                requestLocation.launch(
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                )
                            },
                            onChoosePlace = { choosing = true },
                        )
                    }
                }
            }
        }

        if (choosing) {
            PlaceSheet(model) { choosing = false }
        }

        // Asking exactly when the switch goes on, and only on the versions that ask.
        LaunchedEffect(model.settings.alerts) {
            if (model.settings.alerts && !model.canNotify &&
                android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
            ) {
                requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // A back press inside settings goes back to the weather rather than out of the app.
        androidx.activity.compose.BackHandler(enabled = configuring) { configuring = false }
    }
}
