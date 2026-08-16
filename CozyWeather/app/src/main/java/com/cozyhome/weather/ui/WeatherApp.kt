package com.cozyhome.weather.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cozyhome.weather.ui.home.HomeScreen
import com.cozyhome.weather.ui.intro.CozyIntro

@Composable
fun WeatherApp(viewModel: WeatherViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var introDone by rememberSaveable { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        HomeScreen(
            state = state,
            onRefresh = viewModel::refresh,
            onQueryChange = viewModel::onSearchQuery,
            onSelectPlace = viewModel::selectPlace,
            onUseLocation = viewModel::useDeviceLocation,
            onClearSearch = viewModel::clearSearch,
        )
        AnimatedVisibility(visible = !introDone, exit = fadeOut(tween(250))) {
            CozyIntro(onFinished = { introDone = true })
        }
    }
}
