package app.quire.weather.ui

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
private fun WeatherApp() {
    val model: WeatherModel = viewModel()
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

    LaunchedEffect(Unit) { model.refresh() }

    QuireTheme(dark = isSystemInDarkTheme(), dynamic = true) {
        val scrollBehavior =
            TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
        val refresh = rememberPullToRefreshState()

        Scaffold(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                LargeFlexibleTopAppBar(
                    title = {
                        Text(
                            stringResource(
                                if (configuring) R.string.wx_settings else R.string.weather,
                            ),
                        )
                    },
                    subtitle = {
                        if (!configuring) {
                            model.forecast?.place?.takeIf { it.isNotBlank() }?.let { Text(it) }
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
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { padding ->
            if (configuring) {
                WeatherSettingsScreen(model, padding)
                return@Scaffold
            }
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
                        requestLocation.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    },
                    onChoosePlace = { choosing = true },
                )
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
