package app.quire.calendar.m3

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.quire.calendar.R
import app.quire.weather.DayForecast
import app.quire.weather.Forecast
import app.quire.weather.WeatherRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import kotlin.math.roundToInt

/**
 * The weather, at the length a screen can afford.
 *
 * The widget has to choose what to leave out; this does not, so everything the card had to drop is
 * here — humidity, wind, the chance of rain, and a bar per day showing where that day's swing sits
 * inside the week's. The bar is the part a list of numbers cannot do: it makes a cold Thursday
 * visible without reading a single figure.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WeatherScreen(
    model: WeatherModel,
    padding: PaddingValues,
    onGrant: () -> Unit,
) {
    val forecast = model.forecast
    LazyColumn(contentPadding = padding, modifier = Modifier.fillMaxSize()) {
        if (!model.located) {
            item { LocationCard(onGrant) }
        }
        if (forecast == null) {
            item {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    if (model.located) {
                        LoadingIndicator()
                    } else {
                        Text(
                            text = stringResource(R.string.wx_waiting),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            return@LazyColumn
        }

        item { Now(forecast) }
        item { Readings(forecast) }
        item {
            Text(
                text = stringResource(R.string.wx_five_days),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 24.dp, bottom = 8.dp),
            )
        }
        item { Days(forecast) }
        item { Freshness(forecast) }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun Now(forecast: Forecast) {
    val scheme = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
    ) {
        Text(
            text = forecast.place.ifBlank { stringResource(R.string.weather) },
            style = MaterialTheme.typography.titleMedium,
            color = scheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(forecast.now.sky.icon(forecast.now.day)),
                contentDescription = stringResource(forecast.now.sky.label),
                tint = scheme.primary,
                modifier = Modifier.size(84.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = WeatherRepository.degrees(forecast.now.temperature),
                style = MaterialTheme.typography.displayLarge,
            )
        }
        Text(
            text = stringResource(forecast.now.sky.label),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = stringResource(
                R.string.wx_feels_like,
                WeatherRepository.degrees(forecast.now.feelsLike),
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurfaceVariant,
        )
    }
}

/** The three numbers the widget has no room for, as one row of tonal cards. */
@Composable
private fun Readings(forecast: Forecast) {
    val today = forecast.days.firstOrNull()
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Reading(
            label = stringResource(R.string.wx_rain_chance),
            value = "${today?.rain ?: 0}%",
            modifier = Modifier.weight(1f),
        )
        Reading(
            label = stringResource(R.string.wx_humidity),
            value = if (forecast.now.humidity >= 0) "${forecast.now.humidity}%" else "—",
            modifier = Modifier.weight(1f),
        )
        Reading(
            label = stringResource(R.string.wx_wind),
            value = stringResource(R.string.wx_wind_value, forecast.now.wind.roundToInt()),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun Reading(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 6.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
            Spacer(Modifier.height(4.dp))
            Text(text = value, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun Days(forecast: Forecast) {
    val days = forecast.ahead(5)
    // Every bar is measured against the same week, which is the whole point of drawing them: a
    // day is cold relative to the days on either side of it, not relative to itself.
    val coldest = days.minOfOrNull { it.low } ?: 0.0
    val warmest = days.maxOfOrNull { it.high } ?: 1.0
    val span = (warmest - coldest).coerceAtLeast(1.0)

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(Modifier.padding(vertical = 6.dp)) {
            days.forEach { day -> DayRow(day, coldest, span) }
        }
    }
}

@Composable
private fun DayRow(day: DayForecast, coldest: Double, span: Double) {
    val scheme = MaterialTheme.colorScheme
    val locale = rememberLocale()
    val today = day.date == LocalDate.now()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = if (today) {
                stringResource(R.string.wx_today)
            } else {
                day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
            },
            style = MaterialTheme.typography.bodyLarge,
            color = if (today) scheme.primary else scheme.onSurface,
            modifier = Modifier.width(64.dp),
        )
        Icon(
            painter = painterResource(day.sky.dayIcon),
            contentDescription = stringResource(day.sky.label),
            tint = scheme.onSurfaceVariant,
            modifier = Modifier.size(26.dp),
        )
        Text(
            text = if (day.rain > 0) "${day.rain}%" else "",
            style = MaterialTheme.typography.labelMedium,
            color = scheme.tertiary,
            modifier = Modifier.width(44.dp).padding(start = 6.dp),
        )
        Text(
            text = WeatherRepository.degrees(day.low, locale),
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.width(36.dp),
        )
        Spread(day, coldest, span, Modifier.weight(1f).padding(horizontal = 10.dp))
        Text(
            text = WeatherRepository.degrees(day.high, locale),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(36.dp),
        )
    }
}

/**
 * The day's swing, drawn where it sits in the week's.
 *
 * The bar is inset from both ends in proportion to how far the day's low is above the week's
 * coldest and its high below the week's warmest, so a run of days shows its shape: a cold snap
 * slides left, a warm one right, and a day that swings hard is simply wider.
 */
@Composable
private fun Spread(day: DayForecast, coldest: Double, span: Double, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val start = ((day.low - coldest) / span).toFloat().coerceIn(0f, 1f)
    val end = ((day.high - coldest) / span).toFloat().coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .height(6.dp)
            .clip(CircleShape)
            .background(scheme.surfaceContainerHighest),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(
                    start = 0.dp,
                    end = 0.dp,
                ),
        ) {
            // Placed by fraction rather than by dp so it stays right at any width.
            Box(
                Modifier
                    .fillMaxWidth(fraction = (end - start).coerceAtLeast(0.06f))
                    .fillMaxSize()
                    .offsetFraction(start)
                    .clip(CircleShape)
                    .background(
                        Brush.horizontalGradient(listOf(scheme.tertiary, scheme.primary)),
                    ),
            )
        }
    }
}

/** Offsets a child by a fraction of the parent's width, which no stock modifier does directly. */
private fun Modifier.offsetFraction(fraction: Float): Modifier = this.then(
    Modifier.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) {
            placeable.placeRelative((constraints.maxWidth * fraction).toInt(), 0)
        }
    },
)

@Composable
private fun Freshness(forecast: Forecast) {
    val locale = rememberLocale()
    val stamp = remember(forecast.fetched, locale) {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
            .format(Instant.ofEpochMilli(forecast.fetched).atZone(ZoneId.systemDefault()))
    }
    Text(
        text = stringResource(R.string.wx_updated, stamp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun LocationCard(onGrant: () -> Unit) {
    OutlinedCard(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.wx_no_location),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.wx_no_location_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(onClick = onGrant) {
                Text(stringResource(R.string.wx_grant))
            }
        }
    }
}
