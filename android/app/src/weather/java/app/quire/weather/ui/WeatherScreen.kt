package app.quire.weather.ui

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
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
import app.quire.R
import app.quire.calendar.m3.rememberLocale
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
    onChoosePlace: () -> Unit = {},
) {
    val forecast = model.forecast
    LazyColumn(contentPadding = padding, modifier = Modifier.fillMaxSize()) {
        // Only when there is no place at all. Somebody who named one has answered the question,
        // and being asked again for a permission they declined is nagging rather than helping.
        if (!model.located && !model.pinned) {
            item { LocationCard(onGrant, onChoosePlace) }
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

        item { Now(forecast, model.settings) }
        item { Readings(forecast, model.settings) }
        if (forecast.hours.isNotEmpty()) {
            item { Heading(stringResource(R.string.wx_hours)) }
            item {
                HourStrip(
                    hours = forecast.hoursAhead(java.time.LocalDateTime.now()),
                    units = model.settings,
                )
            }
        }
        forecast.days.firstOrNull()?.let { today ->
            if (today.sunrise != null && today.sunset != null) {
                item { Heading(stringResource(R.string.wx_sun)) }
                item { SunArc(today.sunrise, today.sunset) }
            }
        }
        item { Heading(stringResource(R.string.wx_five_days)) }
        item { Days(forecast, model.settings) }
        item { Freshness(forecast) }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/**
 * What it is doing now.
 *
 * Left-aligned rather than centred: the app bar already carries the place, and a centred stack
 * under a left-aligned bar is the sort of thing that reads as crooked without anybody being able
 * to say why. The number and the sky sit on one line at a size worth the space, and the
 * feels-like appears only when it has something to add — "19°, feels like 19°" is a sentence that
 * spends a line saying nothing.
 */
@Composable
private fun Now(forecast: Forecast, units: WeatherModel.Settings) {
    val scheme = MaterialTheme.colorScheme
    val feels = write(units, forecast.now.feelsLike)
    val actual = write(units, forecast.now.temperature)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 4.dp),
    ) {
        Icon(
            painter = painterResource(forecast.now.sky.icon(forecast.now.day)),
            contentDescription = stringResource(forecast.now.sky.label),
            tint = scheme.primary,
            modifier = Modifier.size(76.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column {
            Text(text = actual, style = MaterialTheme.typography.displayLarge)
            Text(
                text = stringResource(forecast.now.sky.label),
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurfaceVariant,
            )
            if (feels != actual) {
                Text(
                    text = stringResource(R.string.wx_feels_like, feels),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The three numbers the widget has no room for, as one row of tonal cards. */
@Composable
private fun Heading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun Readings(forecast: Forecast, units: WeatherModel.Settings) {
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
            value = "${units.wind.from(forecast.now.wind).roundToInt()} " +
                stringResource(windLabel(units.wind)),
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
private fun Days(forecast: Forecast, units: WeatherModel.Settings) {
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
            days.forEach { day -> DayRow(day, coldest, span, units) }
        }
    }
}

@Composable
private fun DayRow(day: DayForecast, coldest: Double, span: Double, units: WeatherModel.Settings) {
    val scheme = MaterialTheme.colorScheme
    val locale = rememberLocale()
    val today = day.date == LocalDate.now()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        // Short form even for today: "Сегодня" in a 64dp column wraps to two lines and drags the
        // whole row out of alignment, which is exactly what it did.
        Text(
            text = day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale),
            style = MaterialTheme.typography.bodyLarge,
            color = if (today) scheme.primary else scheme.onSurface,
            maxLines = 1,
            modifier = Modifier.width(44.dp),
        )
        Icon(
            painter = painterResource(day.sky.dayIcon),
            contentDescription = stringResource(day.sky.label),
            tint = scheme.onSurfaceVariant,
            modifier = Modifier.size(26.dp),
        )
        // Always written, even at nothing: a column that disappears on dry days leaves the row
        // above and the row below disagreeing about where the temperatures start.
        Text(
            text = if (day.rain > 0) "${day.rain}%" else "—",
            style = MaterialTheme.typography.labelMedium,
            color = if (day.rain > 0) scheme.tertiary else scheme.outlineVariant,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.width(42.dp).padding(start = 6.dp),
        )
        Text(
            text = write(units, day.low),
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.width(36.dp),
        )
        Spread(day, coldest, span, Modifier.weight(1f).padding(horizontal = 10.dp))
        Text(
            text = write(units, day.high),
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

/**
 * The two ways to answer "where".
 *
 * Naming a place is offered first and as the filled button, because it is the one that needs no
 * permission: an app that can only work by being given a location has not left the choice open.
 */
@Composable
private fun LocationCard(onGrant: () -> Unit, onChoosePlace: () -> Unit) {
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
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onChoosePlace) {
                    Text(stringResource(R.string.wx_place))
                }
                FilledTonalButton(onClick = onGrant) {
                    Text(stringResource(R.string.wx_grant))
                }
            }
        }
    }
}

/** A temperature in whatever unit the user asked for. */
private fun write(units: WeatherModel.Settings, celsius: Double): String =
    "${units.degrees.from(celsius).roundToInt()}°"

private fun windLabel(unit: app.quire.weather.WindUnit): Int = when (unit) {
    app.quire.weather.WindUnit.KMH -> R.string.wx_units_kmh
    app.quire.weather.WindUnit.MS -> R.string.wx_units_ms
    app.quire.weather.WindUnit.MPH -> R.string.wx_units_mph
}
