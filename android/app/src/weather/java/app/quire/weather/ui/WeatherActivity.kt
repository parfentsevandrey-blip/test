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
import androidx.compose.runtime.LaunchedEffect
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

    val requestLocation = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) model.permissionGranted() }

    LaunchedEffect(Unit) { model.refresh() }

    QuireTheme(dark = isSystemInDarkTheme(), dynamic = true) {
        val scrollBehavior =
            TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
        val refresh = rememberPullToRefreshState()

        Scaffold(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                LargeFlexibleTopAppBar(
                    title = { Text(stringResource(R.string.weather)) },
                    subtitle = {
                        model.forecast?.place?.takeIf { it.isNotBlank() }?.let { Text(it) }
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { padding ->
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
                WeatherScreen(model, padding) {
                    requestLocation.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                }
            }
        }
    }
}
