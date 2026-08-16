package app.quire.weather.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val scheme = MaterialTheme.colorScheme
    // A sky behind the page: the theme's own container colour at the top, gone by the time the
    // cards start. It carries the one bit of information the screen otherwise only spells out —
    // whether it is day or night out there — and it gives the hero something to sit on other than
    // flat paper. Fixed rather than scrolling, because a sky that scrolls away is a rectangle.
    val sky = if (forecast?.now?.day != false) scheme.primaryContainer else scheme.secondaryContainer
    // It runs from the very top of the window, behind the app bar, which is transparent over this
    // screen for exactly that reason. Starting it under the bar instead put a hard horizontal
    // line across the screen with a square corner at each end — the only edge on a page where
    // everything else is a rounded card, and the thing that made it look stuck on.
    val density = LocalDensity.current
    val reach = with(density) { (padding.calculateTopPadding() + SkyHeight).toPx() }
    val wash = remember(sky, reach) {
        Brush.verticalGradient(
            0f to sky.copy(alpha = 0.62f),
            0.55f to sky.copy(alpha = 0.22f),
            1f to sky.copy(alpha = 0f),
            endY = reach,
        )
    }

    Box(Modifier.fillMaxSize().background(wash)) {
        // The weather itself, moving, under everything else. It is drawn over the same band the
        // wash covers and fades out with it, so the page below stays a page.
        forecast?.takeIf { model.settings.liveSky }?.let {
            LiveSky(
                sky = it.now.sky,
                day = it.now.day,
                // The rain leans the way the wind is actually blowing and as hard as it is
                // actually blowing, which makes the picture one more reading rather than one
                // more ornament.
                windKmh = it.now.wind,
                windFrom = it.now.direction,
                modifier = Modifier
                    .fillMaxWidth()
                    // Shorter than the wash. The colour can run down behind the reading cards
                    // without being noticed; things falling past them cannot.
                    .height(padding.calculateTopPadding() + WeatherHeight)
                    // Clipped, because a draw scope is not: a drop whose head is at the last row
                    // of the band still draws its whole tail below it, at whatever alpha its head
                    // had. Twenty points of very faint rain over the page is not visible and is
                    // still wrong.
                    .clipToBounds(),
            )
        }
        // Which entrance slots have played for this fetch; kept up here because a LazyColumn's
        // content block is a list builder, not a composition, and remembering is composing.
        val ledger = remember(forecast?.fetched) { mutableSetOf<Int>() }
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

            // Each block arrives a beat after the one above it, once per fetch. The ledger
            // remembers which slots have played, so a block scrolled away and back does not
            // perform its entrance again — an entrance repeated is a tic, not a welcome.
            item { Box(Modifier.arrive(0, forecast.fetched, ledger)) { Now(forecast, model.settings) } }
            item { Box(Modifier.arrive(1, forecast.fetched, ledger)) { Readings(forecast, model.settings) } }
            if (forecast.hours.isNotEmpty()) {
                item { Box(Modifier.arrive(2, forecast.fetched, ledger)) { Heading(stringResource(R.string.wx_hours)) } }
                item {
                    Box(Modifier.arrive(2, forecast.fetched, ledger)) {
                        HourStrip(
                            hours = forecast.hoursAhead(java.time.LocalDateTime.now()),
                            units = model.settings,
                        )
                    }
                }
            }
            forecast.days.firstOrNull()?.let { today ->
                if (today.sunrise != null && today.sunset != null) {
                    item { Box(Modifier.arrive(3, forecast.fetched, ledger)) { Heading(stringResource(R.string.wx_sun)) } }
                    item {
                        Box(Modifier.arrive(3, forecast.fetched, ledger)) {
                            SunArc(today.sunrise, today.sunset, model.settings.glassEdges)
                        }
                    }
                }
            }
            item { Box(Modifier.arrive(4, forecast.fetched, ledger)) { Heading(stringResource(R.string.wx_five_days)) } }
            item { Box(Modifier.arrive(4, forecast.fetched, ledger)) { Days(forecast, model.settings) } }
            item { Box(Modifier.arrive(5, forecast.fetched, ledger)) { Freshness(forecast) } }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/**
 * One block's entrance: a fade up from two dozen points below, [slot] beats after the fetch.
 *
 * Played once per forecast. The ledger is what stops a LazyColumn from replaying it — an item
 * scrolled off the screen is disposed, and without a note that its entrance already happened it
 * would perform it again every time it scrolled back in.
 */
@Composable
private fun Modifier.arrive(slot: Int, fetched: Long, ledger: MutableSet<Int>): Modifier {
    val played = slot in ledger
    val progress = remember(fetched, slot) {
        androidx.compose.animation.core.Animatable(if (played) 1f else 0f)
    }
    LaunchedEffect(fetched, slot) {
        if (!played) {
            kotlinx.coroutines.delay(slot * 70L)
            progress.animateTo(
                1f,
                androidx.compose.animation.core.tween(
                    durationMillis = 420,
                    easing = androidx.compose.animation.core.FastOutSlowInEasing,
                ),
            )
            ledger += slot
        }
    }
    return this.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 24.dp.toPx()
    }
}

/** How far down the wash reaches: the hero and the readings, and nothing after them. */
private val SkyHeight = 320.dp

/** How far down the weather falls, which is as far as the hero and no further. */
private val WeatherHeight = 232.dp

/**
 * What it is doing now.
 *
 * A column, every line of it starting at [Gutter], so the number, the sky and the cards below all
 * share one left edge. The earlier version put a 76dp icon to the left of a two-line column and
 * centred the two against each other, which meant the icon, the number and the word underneath it
 * each began at a different x — three edges in a block four lines tall, and the reason the screen
 * read as crooked before anything else on it did.
 *
 * The sky and the feels-like share a line, and the feels-like appears only when it has something
 * to add: "19°, feels like 19°" is a sentence that spends a line saying nothing.
 */
@Composable
private fun Now(forecast: Forecast, units: WeatherModel.Settings) {
    val scheme = MaterialTheme.colorScheme
    val feels = write(units, forecast.now.feelsLike)
    val actual = write(units, forecast.now.temperature)
    val sky = stringResource(forecast.now.sky.label)

    Column(Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = actual,
                // The number is the reason the app was opened, and it is set like it: ninety-two
                // points of the lightest weight the face carries, with ink that turns toward the
                // accent on its way down. Display size is the one place a gradient can live in
                // the letterforms without hurting them — at this scale it reads as light on the
                // figure, where the same brush on body text would read as a misprint — and the
                // hairline weight is what keeps that much type from being a wall.
                style = MaterialTheme.typography.displayLarge.merge(
                    androidx.compose.ui.text.TextStyle(
                        fontSize = 92.sp,
                        lineHeight = 96.sp,
                        fontWeight = FontWeight.W200,
                        letterSpacing = (-2).sp,
                        brush = Brush.linearGradient(
                            listOf(scheme.onSurface, scheme.primary),
                        ),
                    ),
                ),
                maxLines = 1,
            )
            Spacer(Modifier.width(16.dp))
            Icon(
                painter = painterResource(forecast.now.sky.icon(forecast.now.day)),
                contentDescription = sky,
                tint = scheme.primary,
                modifier = Modifier.size(64.dp),
            )
        }
        Text(
            text = if (feels == actual) {
                sky
            } else {
                sky + " · " + stringResource(R.string.wx_feels_like_short, feels)
            },
            style = MaterialTheme.typography.titleMedium,
            color = scheme.onSurfaceVariant,
        )
        // Today's range under the reading it belongs to. The five-day card carries it too, four
        // scrolls down; up here it answers the question the current temperature raises and does
        // not settle — whether this is the warm part of the day or the cold one.
        forecast.days.firstOrNull()?.let { today ->
            Spacer(Modifier.height(8.dp))
            // As two small pills rather than a line of type: the day's bounds are the second
            // thing the hero answers, and a pill is how this design says "a fact you can lean
            // on" everywhere else — the hour strip's Now, today in the five days.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RangePill("↑", write(units, today.high), scheme.primary)
                RangePill("↓", write(units, today.low), scheme.tertiary)
            }
        }
    }
}

/** One bound of the day, worn as a pill: the mark in the day's own accent, the number in ink. */
@Composable
private fun RangePill(mark: String, value: String, accent: androidx.compose.ui.graphics.Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        Text(
            text = mark,
            style = MaterialTheme.typography.labelLarge,
            color = accent,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/** A section heading, on the same left edge as the block it names. */
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

/** One cell of the slab: its mark, its words, its number, and — for the wind — its bearing. */
private data class Meter(val icon: Int, val label: String, val value: String, val turn: Float? = null)

/**
 * The three numbers the widget has no room for, as one row of tonal cards.
 *
 * The row is measured at [IntrinsicSize.Min] and the cards fill it, so all three end at the same
 * height whatever their labels do. Without that, one label wrapping to a second line made its card
 * taller than the two beside it and left the row with a ragged bottom.
 */
@Composable
private fun Readings(forecast: Forecast, units: WeatherModel.Settings) {
    val today = forecast.days.firstOrNull()
    val now = forecast.now
    val compass = stringArrayResource(R.array.wx_compass)

    // Everything the widget has no room for, in the order it gets asked about. A reading the
    // provider did not send is left out entirely rather than shown as a dash: a card that says
    // "—" is a card spent saying nothing.
    val readings = buildList {
        add(
            Meter(
                R.drawable.wx_drop,
                stringResource(R.string.wx_rain_chance_short),
                "${today?.rain ?: 0}%",
            ),
        )
        if (now.humidity >= 0) {
            add(
                Meter(
                    R.drawable.wx_humidity,
                    stringResource(R.string.wx_humidity),
                    "${now.humidity}%",
                ),
            )
        }
        add(
            Meter(
                // With a bearing the mark is a needle turned to where the wind is going — the
                // direction named in the label is where it comes FROM, and what it pushes things
                // along by is the opposite. Without one, the generic gusts glyph.
                if (now.quarter != null) R.drawable.wx_needle else R.drawable.wx_wind,
                // The quarter it blows from goes in the label rather than the value: it is what
                // kind of wind this is, not how much of it there is.
                now.quarter?.let { stringResource(R.string.wx_wind_from, compass[it]) }
                    ?: stringResource(R.string.wx_wind),
                "${units.wind.from(now.wind).roundToInt()} " + stringResource(windLabel(units.wind)),
                turn = if (now.quarter != null) ((now.direction + 180) % 360).toFloat() else null,
            ),
        )
        if (now.gust >= 0) {
            add(
                Meter(
                    R.drawable.wx_gust,
                    stringResource(R.string.wx_gust),
                    "${units.wind.from(now.gust).roundToInt()} " +
                        stringResource(windLabel(units.wind)),
                ),
            )
        }
        if (now.uv >= 0) {
            add(Meter(R.drawable.wx_uv, stringResource(R.string.wx_uv), "${now.uv.roundToInt()}"))
        }
        if (now.pressure >= 0) {
            add(
                Meter(
                    R.drawable.wx_pressure,
                    stringResource(R.string.wx_pressure),
                    "${units.pressure.from(now.pressure).roundToInt()} " +
                        stringResource(pressureLabel(units.pressure)),
                ),
            )
        }
    }

    // One slab, sliced. Six loose cards with equal gaps everywhere are six things; the same six
    // with hairline gaps inside and the outer corners rounded as a group are one object with six
    // readings on it — the connected-block grammar Android's own settings established, and the
    // same one this app's settings screens already speak.
    val rows = readings.chunked(3)
    Column(
        verticalArrangement = Arrangement.spacedBy(SlabGap),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Gutter, vertical = 12.dp)
            .testTag(BLOCK),
    ) {
        rows.forEachIndexed { line, row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(SlabGap),
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            ) {
                row.forEachIndexed { column, meter ->
                    // Its place in the grid is its seed, so no two cells carry the same light in
                    // the same place at the same moment — and its shape, because which corners
                    // are the slab's own is a fact about where the cell sits.
                    Reading(
                        meter, units,
                        seed = line * 3 + column,
                        shape = cellShape(
                            firstRow = line == 0,
                            lastRow = line == rows.size - 1,
                            firstCol = column == 0,
                            lastCol = column == row.size - 1,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                }
                // A short last row keeps the cards the width of the ones above rather than
                // stretching two of them across three columns.
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Which corners of a cell belong to the slab, from where the cell sits in it. */
private fun cellShape(
    firstRow: Boolean,
    lastRow: Boolean,
    firstCol: Boolean,
    lastCol: Boolean,
): RoundedCornerShape = RoundedCornerShape(
    topStart = if (firstRow && firstCol) CardCorner else CellCorner,
    topEnd = if (firstRow && lastCol) CardCorner else CellCorner,
    bottomStart = if (lastRow && firstCol) CardCorner else CellCorner,
    bottomEnd = if (lastRow && lastCol) CardCorner else CellCorner,
)

/** The gap inside the slab, and the corner a cell keeps where it meets another cell. */
private val SlabGap = 3.dp
private val CellCorner = 7.dp

/**
 * One reading: its mark, the number, and the word for it.
 *
 * The number comes before the word because that is the order it is read in — you look at a card
 * like this to find out what the humidity is, not to be reminded that humidity exists.
 */
@Composable
private fun Reading(
    meter: Meter,
    units: WeatherModel.Settings,
    seed: Int,
    shape: RoundedCornerShape,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = shape,
        modifier = modifier
            .fillMaxHeight()
            .glass(shape, units.glassEdges, seed),
        elevation = CardDefaults.cardElevation(defaultElevation = CardLift),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 6.dp),
        ) {
            Icon(
                painter = painterResource(meter.icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(18.dp)
                    .then(meter.turn?.let { Modifier.rotate(it) } ?: Modifier),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = meter.value,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            Text(
                text = meter.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun Days(forecast: Forecast, units: WeatherModel.Settings) {
    val days = forecast.ahead(5)
    // Every bar is measured against the same week, which is the whole point of drawing them: a
    // day is cold relative to the days on either side of it, not relative to itself.
    val coldest = days.minOfOrNull { it.low } ?: 0.0
    val warmest = days.maxOfOrNull { it.high } ?: 1.0
    val span = (warmest - coldest).coerceAtLeast(1.0)

    Card(
        shape = RoundedCornerShape(CardCorner),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Gutter)
            .testTag(BLOCK)
            .glass(RoundedCornerShape(CardCorner), units.glassEdges, seed = 11),
        elevation = CardDefaults.cardElevation(defaultElevation = CardLift),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        // One open at a time: a card with every day unfolded is the long screen this screen
        // replaced. Opening a second day closes the first, the way an accordion holds one note.
        var open by remember { mutableIntStateOf(-1) }
        val haptics = LocalHapticFeedback.current
        Column(Modifier.padding(vertical = 6.dp)) {
            days.forEachIndexed { index, day ->
                DayRow(
                    day = day,
                    coldest = coldest,
                    span = span,
                    units = units,
                    index = index,
                    open = open == index,
                    onToggle = {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        open = if (open == index) -1 else index
                    },
                )
            }
        }
    }
}

@Composable
private fun DayRow(
    day: DayForecast,
    coldest: Double,
    span: Double,
    units: WeatherModel.Settings,
    index: Int,
    open: Boolean,
    onToggle: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val locale = rememberLocale()
    val today = day.date == LocalDate.now()

    // The chevron leans into the row's state on a spring rather than snapping, which is the whole
    // of the affordance: a mark that moves when touched is a mark that says it can be.
    val lean by animateFloatAsState(
        targetValue = if (open) 180f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "lean",
    )

    Column(
        // Today wears the same tonal pill the hour strip's "Now" does, so the two cards point at
        // the present the same way. The pill is inset from the card's edge and the row's content
        // keeps its old inset in total, so the columns still line up with the rows around them.
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .then(
                if (today) {
                    Modifier.background(scheme.surfaceContainerHighest)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onToggle)
            .testTag("day-$index")
            .padding(horizontal = 10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        ) {
            // Short form even for today: "Сегодня" in a 64dp column wraps to two lines and drags
            // the whole row out of alignment, which is exactly what it did.
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
        // The column keeps its width on a dry day but writes nothing in it. It has to keep the
        // width or the rows below disagree about where the temperatures start; it must not write a
        // dash, because a dash in a column of percentages reads as a stray minus sign rather than
        // as "none", which is what it looked like on a real phone.
        Text(
            text = if (day.rain > 0) "${day.rain}%" else "",
            style = MaterialTheme.typography.labelMedium,
            color = scheme.tertiary,
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
            Icon(
                painter = painterResource(R.drawable.wx_more),
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(16.dp)
                    .rotate(lean),
            )
        }

        // The rest of the day, under the row that names it: when the sun is up and down, and the
        // word for the sky. It grows out on the theme's spring and takes the day's own accent for
        // its times, so the open row answers the two questions a plain row raises — how long is
        // the light, and what kind of day is it.
        AnimatedVisibility(
            visible = open,
            enter = expandVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ) + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            val clock = remember(locale) {
                DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth().padding(start = 44.dp, bottom = 12.dp),
            ) {
                Text(
                    text = stringResource(day.sky.label),
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (day.sunrise != null) {
                    Text(
                        text = stringResource(R.string.wx_sunrise) + " " +
                            clock.format(day.sunrise),
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.primary,
                    )
                }
                if (day.sunset != null) {
                    Text(
                        text = stringResource(R.string.wx_sunset) + " " + clock.format(day.sunset),
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.tertiary,
                    )
                }
            }
        }
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
    OutlinedCard(modifier = Modifier.fillMaxWidth().padding(Gutter)) {
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

private fun pressureLabel(unit: app.quire.weather.Pressure): Int = when (unit) {
    app.quire.weather.Pressure.HPA -> R.string.wx_units_hpa
    app.quire.weather.Pressure.MMHG -> R.string.wx_units_mmhg
}
