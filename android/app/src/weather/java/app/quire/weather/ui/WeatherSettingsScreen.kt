package app.quire.weather.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.quire.R
import app.quire.calendar.m3.ChoiceRow
import app.quire.calendar.m3.SettingGroup
import app.quire.calendar.m3.SettingRow
import app.quire.weather.Degrees
import app.quire.weather.Pressure
import app.quire.weather.WeatherRefresh
import app.quire.weather.WeatherSettings
import app.quire.weather.WindUnit
import kotlin.math.roundToInt

/**
 * Everything the weather app can be told.
 *
 * Laid out the same way the calendar's settings are — grouped, connected blocks with the outer
 * corners rounded — because they are the same app wearing a different hat, and a person who has
 * used one should not have to learn the other. Every block starts at [Gutter], the same edge the
 * weather screen's cards start at, so moving between the two is not a step sideways.
 */
@Composable
fun WeatherSettingsScreen(model: WeatherModel, padding: PaddingValues) {
    val settings = model.settings
    LazyColumn(contentPadding = padding, modifier = Modifier.fillMaxSize()) {
        item { Heading(stringResource(R.string.wx_section_updates)) }
        item {
            // Chips rather than one segmented row. Six segments across a phone leaves about
            // fifty points each, which cut "1 hour", "3 hours" and "6 hours" down to "1", "3"
            // and "6" — three intervals a person could not tell apart, and the unit dropped from
            // exactly the options where it mattered. Chips wrap instead of shrinking.
            PeriodRow(
                selected = settings.period,
                onSelect = { model.setPeriod(it) },
                // Two hints, because the short intervals come with a caveat the long ones do not
                // and burying it would be the same as not saying it.
                hint = stringResource(R.string.wx_period_hint) +
                    if (settings.period < WeatherRefresh.JOB_FLOOR_MINUTES) {
                        "\n" + stringResource(R.string.wx_period_short_hint)
                    } else {
                        ""
                    },
            )
        }

        item { Heading(stringResource(R.string.wx_section_look)) }
        item {
            SettingGroup {
                SettingRow(
                    index = 0,
                    count = 1,
                    title = stringResource(R.string.wx_live_sky),
                    hint = stringResource(R.string.wx_live_sky_hint),
                    checked = settings.liveSky,
                    onChange = { model.setLiveSky(it) },
                )
            }
        }

        item { Heading(stringResource(R.string.wx_section_alerts)) }
        item {
            SettingGroup {
                SettingRow(
                    index = 0,
                    count = 1,
                    title = stringResource(R.string.wx_alerts),
                    hint = stringResource(R.string.wx_alerts_hint),
                    checked = settings.alerts,
                    onChange = { model.setAlerts(it) },
                )
            }
        }
        if (settings.alerts) {
            item {
                val haptics = LocalHapticFeedback.current
                Column(Modifier.padding(horizontal = Gutter, vertical = 8.dp)) {
                    Text(
                        text = stringResource(R.string.wx_threshold, settings.threshold),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Slider(
                        value = settings.threshold.toFloat(),
                        onValueChange = {
                            // One light tick per step the thumb lands on, not per pixel it moves.
                            if (it.roundToInt() != settings.threshold) {
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                            }
                            model.setThreshold(it.roundToInt())
                        },
                        valueRange = WeatherSettings.MIN_THRESHOLD.toFloat()..
                            WeatherSettings.MAX_THRESHOLD.toFloat(),
                        steps = 6,
                    )
                    if (!model.canNotify) {
                        Text(
                            text = stringResource(R.string.wx_notifications_blocked),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }

        item { Heading(stringResource(R.string.wx_section_units)) }
        item {
            ChoiceRow(
                title = stringResource(R.string.wx_units_temperature),
                options = listOf(
                    stringResource(R.string.wx_units_celsius),
                    stringResource(R.string.wx_units_fahrenheit),
                ),
                selected = Degrees.entries.indexOf(settings.degrees),
                onSelect = { model.setDegrees(Degrees.entries[it]) },
            )
        }
        item {
            ChoiceRow(
                title = stringResource(R.string.wx_units_wind),
                options = listOf(
                    stringResource(R.string.wx_units_kmh),
                    stringResource(R.string.wx_units_ms),
                    stringResource(R.string.wx_units_mph),
                ),
                selected = WindUnit.entries.indexOf(settings.wind),
                onSelect = { model.setWind(WindUnit.entries[it]) },
            )
        }
        item {
            ChoiceRow(
                title = stringResource(R.string.wx_units_pressure),
                options = listOf(
                    stringResource(R.string.wx_units_hpa),
                    stringResource(R.string.wx_units_mmhg),
                ),
                selected = Pressure.entries.indexOf(settings.pressure),
                onSelect = { model.setPressure(Pressure.entries[it]) },
            )
        }

        item { Spacer(Modifier.height(32.dp)) }
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(
            start = Gutter,
            end = Gutter,
            top = HeadingTop,
            bottom = HeadingBottom,
        ),
    )
}

/** The six intervals, as chips that keep their units by wrapping onto a second line. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PeriodRow(selected: Int, onSelect: (Int) -> Unit, hint: String) {
    val labels = listOf(
        stringResource(R.string.wx_period_5),
        stringResource(R.string.wx_period_10),
        stringResource(R.string.wx_period_30),
        stringResource(R.string.wx_period_60),
        stringResource(R.string.wx_period_180),
        stringResource(R.string.wx_period_360),
    )
    val haptics = LocalHapticFeedback.current
    Column(Modifier.padding(horizontal = Gutter, vertical = 8.dp)) {
        Text(stringResource(R.string.wx_period), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WeatherSettings.PERIODS.forEachIndexed { index, minutes ->
                FilterChip(
                    selected = minutes == selected,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onSelect(minutes)
                    },
                    label = { Text(labels[index], maxLines = 1) },
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
