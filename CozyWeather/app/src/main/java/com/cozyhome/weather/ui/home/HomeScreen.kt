package com.cozyhome.weather.ui.home

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.cozyhome.weather.R
import com.cozyhome.weather.data.GeoPlace
import com.cozyhome.weather.data.WeatherKind
import com.cozyhome.weather.data.WeatherSnapshot
import com.cozyhome.weather.data.cozyLine
import com.cozyhome.weather.data.emoji
import com.cozyhome.weather.data.weatherDescription
import com.cozyhome.weather.ui.UiState
import com.cozyhome.weather.ui.scenes.SceneBackground
import com.cozyhome.weather.util.Haptics
import com.cozyhome.weather.util.formatDayOfWeek
import com.cozyhome.weather.util.formatHour
import com.cozyhome.weather.util.formatPressure
import com.cozyhome.weather.util.formatTemp
import com.cozyhome.weather.util.formatUpdatedAt
import com.cozyhome.weather.util.formatWind
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: UiState,
    onRefresh: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSelectPlace: (GeoPlace) -> Unit,
    onUseLocation: () -> Unit,
    onClearSearch: () -> Unit,
) {
    val context = LocalContext.current
    val snapshot = state.snapshot
    val current = snapshot?.forecast?.current
    val kind = current?.let { WeatherKind.fromCode(it.weatherCode) } ?: WeatherKind.CLOUDY
    val isDay = current?.isDay != 0

    Box(Modifier.fillMaxSize().background(Color(0xFF1D1B2C))) {
        SceneBackground(kind, isDay, Modifier.fillMaxSize())

        val topPad = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 88.dp
        val bottomPad = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 28.dp

        PullToRefreshBox(
            isRefreshing = state.refreshing && snapshot != null,
            onRefresh = {
                Haptics.tick(context)
                onRefresh()
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = topPad, bottom = bottomPad),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (snapshot != null) {
                    item(key = "hero") { Hero(snapshot) }
                    item(key = "hourly") { HourlyCard(snapshot, index = 1) }
                    item(key = "daily") { DailyCard(snapshot, index = 2) }
                    item(key = "details") { DetailsGrid(snapshot) }
                    item(key = "footer") {
                        Text(
                            text = stringResource(R.string.updated) + " " + formatUpdatedAt(snapshot.fetchedAtEpochMs),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }

        SearchOverlay(
            state = state,
            onQueryChange = onQueryChange,
            onSelectPlace = onSelectPlace,
            onUseLocation = onUseLocation,
            onClearSearch = onClearSearch,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        if (snapshot == null) {
            if (state.error != null) {
                ErrorCard(onRefresh, Modifier.align(Alignment.Center))
            } else {
                LoadingHint(Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
private fun Hero(snapshot: WeatherSnapshot) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val current = snapshot.forecast.current
    val kind = WeatherKind.fromCode(current.weatherCode)
    val isDay = current.isDay == 1
    val bounce = remember { Animatable(1f) }

    Column(
        modifier = Modifier.fillMaxWidth().floaty(0, amplitude = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.Place,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = snapshot.place.name,
                style = MaterialTheme.typography.titleLarge.copy(
                    shadow = Shadow(Color.Black.copy(alpha = 0.4f), Offset(0f, 3f), 12f),
                ),
                color = Color.White,
            )
        }

        AnimatedContent(
            targetState = formatTemp(current.temperature),
            transitionSpec = {
                (slideInVertically { it / 2 } + fadeIn()) togetherWith
                    (slideOutVertically { -it / 2 } + fadeOut()) using SizeTransform(clip = false)
            },
            label = "temp",
        ) { temp ->
            Text(
                text = temp,
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = bounce.value
                        scaleY = bounce.value
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        Haptics.click(context)
                        scope.launch {
                            bounce.animateTo(1.07f, spring(stiffness = 2400f))
                            bounce.animateTo(1f, spring(dampingRatio = 0.3f, stiffness = 260f))
                        }
                    },
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 108.sp,
                    fontWeight = FontWeight.SemiBold,
                    shadow = Shadow(Color.Black.copy(alpha = 0.35f), Offset(0f, 6f), 22f),
                ),
                color = Color.White,
            )
        }

        Text(
            text = kind.emoji(isDay) + "  " + weatherDescription(current.weatherCode),
            style = MaterialTheme.typography.titleMedium.copy(
                shadow = Shadow(Color.Black.copy(alpha = 0.4f), Offset(0f, 2f), 10f),
            ),
            color = Color.White,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = kind.cozyLine(isDay),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.75f),
        )
        Spacer(Modifier.height(12.dp))
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
        ) {
            Text(
                text = stringResource(R.string.feels_like) + " " + formatTemp(current.feelsLike),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun HourlyCard(snapshot: WeatherSnapshot, index: Int) {
    val context = LocalContext.current
    val hourly = snapshot.forecast.hourly
    val startIdx = remember(snapshot) {
        hourly.time.indexOfFirst { it >= snapshot.forecast.current.time }.coerceAtLeast(0)
    }
    val count = minOf(24, hourly.time.size - startIdx)

    FloatingCard(index) {
        Text(
            text = stringResource(R.string.hourly),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            items(count) { j ->
                val i = startIdx + j
                val code = hourly.weatherCode.getOrElse(i) { 0 }
                val hr = hourly.time[i].substringAfter('T').take(2).toIntOrNull() ?: 12
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { Haptics.tick(context) },
                ) {
                    Text(
                        text = if (j == 0) "Сейчас" else formatHour(hourly.time[i]),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(WeatherKind.fromCode(code).emoji(hr in 6..20), fontSize = 22.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = formatTemp(hourly.temperature.getOrElse(i) { 0.0 }),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    val p = hourly.precipitationProbability.getOrNull(i)
                    if (p != null && p >= 20) {
                        Text(
                            text = "$p%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyCard(snapshot: WeatherSnapshot, index: Int) {
    val daily = snapshot.forecast.daily
    FloatingCard(index) {
        Text(
            text = stringResource(R.string.daily),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        daily.time.forEachIndexed { i, date ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatDayOfWeek(date, i),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(WeatherKind.fromCode(daily.weatherCode.getOrElse(i) { 0 }).emoji(true), fontSize = 18.sp)
                Spacer(Modifier.width(16.dp))
                Text(
                    text = formatTemp(daily.temperatureMin.getOrElse(i) { 0.0 }),
                    modifier = Modifier.width(52.dp),
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatTemp(daily.temperatureMax.getOrElse(i) { 0.0 }),
                    modifier = Modifier.width(56.dp),
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (i < daily.time.lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }
        }
    }
}

@Composable
private fun DetailsGrid(snapshot: WeatherSnapshot) {
    val c = snapshot.forecast.current
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            DetailChip(
                icon = { Icon(Icons.Rounded.Thermostat, null, tint = MaterialTheme.colorScheme.primary) },
                label = stringResource(R.string.feels_like),
                value = formatTemp(c.feelsLike),
                index = 3,
                modifier = Modifier.weight(1f),
            )
            DetailChip(
                icon = { Icon(Icons.Rounded.WaterDrop, null, tint = MaterialTheme.colorScheme.primary) },
                label = stringResource(R.string.humidity),
                value = "${c.humidity}%",
                index = 4,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            DetailChip(
                icon = { Icon(Icons.Rounded.Air, null, tint = MaterialTheme.colorScheme.primary) },
                label = stringResource(R.string.wind),
                value = formatWind(c.windSpeed),
                index = 5,
                modifier = Modifier.weight(1f),
            )
            DetailChip(
                icon = { Icon(Icons.Rounded.Speed, null, tint = MaterialTheme.colorScheme.primary) },
                label = stringResource(R.string.pressure),
                value = formatPressure(c.pressure),
                index = 6,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DetailChip(
    icon: @Composable () -> Unit,
    label: String,
    value: String,
    index: Int,
    modifier: Modifier = Modifier,
) {
    FloatingCard(index, modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon()
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun SearchOverlay(
    state: UiState,
    onQueryChange: (String) -> Unit,
    onSelectPlace: (GeoPlace) -> Unit,
    onUseLocation: () -> Unit,
    onClearSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Haptics.success(context)
            onUseLocation()
        }
    }

    Column(modifier.statusBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp)) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
            shadowElevation = 4.dp,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 15.dp)) {
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(R.string.search_hint),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = {
                            query = it
                            onQueryChange(it)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        singleLine = true,
                    )
                }
                if (query.isNotEmpty()) {
                    IconButton(onClick = {
                        Haptics.tick(context)
                        query = ""
                        onClearSearch()
                        keyboard?.hide()
                    }) {
                        Icon(Icons.Rounded.Close, contentDescription = null)
                    }
                }
                IconButton(onClick = {
                    Haptics.click(context)
                    val granted = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) onUseLocation()
                    else permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                }) {
                    Icon(
                        Icons.Rounded.MyLocation,
                        contentDescription = stringResource(R.string.my_location),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = state.searchResults.isNotEmpty() || state.searching,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
                shadowElevation = 6.dp,
            ) {
                Column(Modifier.padding(vertical = 6.dp)) {
                    if (state.searching && state.searchResults.isEmpty()) {
                        Text(
                            text = "Ищем…",
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    state.searchResults.forEach { place ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    Haptics.success(context)
                                    query = ""
                                    keyboard?.hide()
                                    onSelectPlace(place)
                                }
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                        ) {
                            Text(
                                text = place.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            val detail = listOfNotNull(place.admin1, place.country).joinToString(", ")
                            if (detail.isNotEmpty()) {
                                Text(
                                    text = detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoadingHint(modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        LoadingIndicator(modifier = Modifier.size(76.dp))
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.loading),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
        )
    }
}

@Composable
private fun ErrorCard(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.padding(32.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("🌧️", fontSize = 44.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.error_network),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry) {
                Text(stringResource(R.string.retry))
            }
        }
    }
}
