package app.rosa.calendar.ui.month

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.rosa.calendar.R
import app.rosa.calendar.data.Occurrence
import app.rosa.calendar.ui.CalendarActions
import app.rosa.calendar.ui.CalendarWeather
import app.rosa.calendar.ui.WeatherState
import app.rosa.calendar.ui.currentLocale
import app.rosa.weather.core.designsystem.component.GlassButton
import app.rosa.weather.core.designsystem.component.GlassSurface
import app.rosa.weather.core.designsystem.component.RosaIcon
import app.rosa.weather.core.designsystem.component.RosaIconView
import app.rosa.weather.core.designsystem.component.WeatherGlyph
import app.rosa.weather.core.designsystem.format.WeatherFormat
import app.rosa.weather.core.designsystem.glass.GlassStyle
import app.rosa.weather.core.designsystem.haptics.LocalHaptics
import app.rosa.weather.core.designsystem.motion.RosaMotion
import app.rosa.weather.core.designsystem.theme.Rosa
import app.rosa.weather.core.model.WeatherCondition
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

/**
 * The chosen day on its own pane of glass: its name, its weather from the forecast, its events —
 * each opening in the phone's calendar — and a new event on it. A newly chosen day settles in.
 */
@Composable
internal fun DayCard(
    date: LocalDate,
    today: LocalDate,
    events: List<Occurrence>,
    weather: WeatherState,
    showEvents: Boolean,
    eventsAllowed: Boolean,
    onAllowEvents: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptics = LocalHaptics.current
    GlassSurface(
        modifier.fillMaxWidth(),
        style = GlassStyle.Frosted,
        cornerRadius = 30.dp,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 16.dp),
    ) {
        AnimatedContent(
            date,
            transitionSpec = {
                (fadeIn(tween(240)) + scaleIn(RosaMotion.gel(), initialScale = 0.97f)) togetherWith fadeOut(tween(120)) using
                    SizeTransform(clip = false) { _, _ -> RosaMotion.gel() }
            },
            label = "day",
        ) { day ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DayTitle(day, today, weather)
                if (showEvents) {
                    when {
                        !eventsAllowed -> PermissionLine(onAllowEvents)
                        events.isEmpty() -> Text(stringResource(R.string.day_no_events), style = Rosa.type.body, color = Rosa.colors.inkSoft)
                        else -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            events.forEach { occurrence -> EventRow(occurrence, day) }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlassButton(
                        onClick = {
                            haptics?.confirm()
                            CalendarActions.addEvent(context, day, today)
                        },
                        prominent = true,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 11.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RosaIconView(RosaIcon.Plus, Rosa.colors.ink, size = 16.dp)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.day_add), style = Rosa.type.label, color = Rosa.colors.ink)
                        }
                    }
                    GlassButton(
                        onClick = {
                            haptics?.tick()
                            CalendarActions.openDay(context, day)
                        },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 11.dp),
                    ) {
                        Text(stringResource(R.string.day_open), style = Rosa.type.label, color = Rosa.colors.ink, maxLines = 1)
                    }
                }
                WeatherSource(weather)
            }
        }
    }
}

/** "Tuesday", "29 September" — and, when the forecast has the day, its weather. */
@Composable
private fun DayTitle(date: LocalDate, today: LocalDate, weather: WeatherState) {
    val context = LocalContext.current
    val locale = currentLocale()
    val lent = (weather as? WeatherState.Lent)?.weather
    val format = remember(lent?.units, lent?.zone) { lent?.let { WeatherFormat(context, it.units, it.zone) } }
    val day = lent?.days?.get(date)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                date.dayOfWeek.getDisplayName(TextStyle.FULL_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) },
                style = Rosa.type.title,
                color = Rosa.colors.ink,
                maxLines = 1,
                modifier = Modifier.semantics { heading() },
            )
            val todayWord = stringResource(R.string.events_today)
            val tomorrowWord = stringResource(R.string.events_tomorrow)
            val dateLine = remember(date, today, locale, todayWord) {
                val text = DateTimeFormatter.ofPattern("d MMMM", locale).format(date)
                when (date) {
                    today -> "$todayWord, $text"
                    today.plusDays(1) -> "$tomorrowWord, $text"
                    else -> text
                }
            }
            Text(dateLine, style = Rosa.type.label, color = Rosa.colors.inkSoft, maxLines = 1)
        }
        if (day != null && format != null) {
            val condition = WeatherCondition.fromWmo(day.weatherCode)
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WeatherGlyph(condition, isDay = true, modifier = Modifier.size(34.dp), animated = true)
                    Spacer(Modifier.width(8.dp))
                    Text(format.temperature(day.temperatureMax), style = Rosa.type.numeral, color = Rosa.colors.ink)
                    Text(" / " + format.temperature(day.temperatureMin), style = Rosa.type.body, color = Rosa.colors.inkSoft)
                }
                val detail = if (date == today) {
                    stringResource(R.string.day_now, format.temperature(lent.now.temperature))
                } else if (day.precipitationProbabilityMax >= 20) {
                    stringResource(R.string.day_precipitation, day.precipitationProbabilityMax)
                } else {
                    format.condition(condition)
                }
                Text(detail, style = Rosa.type.caption, color = Rosa.colors.inkSoft, maxLines = 1)
            }
        }
    }
}

/** One event: its calendar's colour, its time, what it is and where. */
@Composable
internal fun EventRow(occurrence: Occurrence, date: LocalDate, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val haptics = LocalHaptics.current
    val time = remember(occurrence, date) { CalendarActions.timeOf(context, occurrence, date) }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clickable(remember { MutableInteractionSource() }, null, role = Role.Button) {
                haptics?.tick()
                CalendarActions.openEvent(context, occurrence)
            }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val color = Color(occurrence.color or 0xFF000000.toInt())
        Canvas(Modifier.width(4.dp).height(38.dp)) {
            drawRoundRect(color, cornerRadius = CornerRadius(size.width / 2))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                occurrence.title.ifBlank { "—" },
                style = Rosa.type.body,
                color = Rosa.colors.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val line = listOf(time, occurrence.location).filter { it.isNotBlank() }.joinToString(" · ")
            Text(line, style = Rosa.type.caption, color = Rosa.colors.inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Events need the calendars: why, and the way to allow it. */
@Composable
internal fun PermissionLine(onAllow: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.events_permission_title), style = Rosa.type.headline, color = Rosa.colors.ink)
            Text(stringResource(R.string.events_permission_body), style = Rosa.type.caption, color = Rosa.colors.inkSoft)
        }
        Spacer(Modifier.width(12.dp))
        GlassButton(onClick = onAllow, prominent = true, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
            Text(stringResource(R.string.events_permission_allow), style = Rosa.type.label, color = Rosa.colors.ink)
        }
    }
}

/** Where the weather comes from, or why there is none. */
@Composable
internal fun WeatherSource(weather: WeatherState) {
    val text = when (weather) {
        is WeatherState.Lent -> stringResource(R.string.weather_from, placeName(weather.weather))
        WeatherState.Waiting -> stringResource(R.string.weather_waiting)
        WeatherState.NoWeatherApp -> stringResource(R.string.weather_missing)
        WeatherState.Off -> return
    }
    Box(Modifier.fillMaxWidth().padding(top = 2.dp)) {
        Text(text, style = Rosa.type.caption, color = Rosa.colors.inkFaint)
    }
}

@Composable
private fun placeName(weather: CalendarWeather): String =
    if (weather.isCurrentLocation || weather.placeName.isBlank()) {
        weather.placeName.ifBlank { stringResource(R.string.weather_current) }
    } else {
        weather.placeName
    }
