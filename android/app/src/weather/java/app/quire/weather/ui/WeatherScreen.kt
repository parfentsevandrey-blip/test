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
import androidx.compose.foundation.shape.CornerSize
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
import androidx.compose.ui.geometry.Offset
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
import app.quire.weather.Sky
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
    // A sky behind the page: gone by the time the cards start, and no longer a two-state lamp.
    // The forecast already carries today's sunrise and sunset, so the wash knows six in the
    // morning from noon from the golden hour from deep night — SkyMoment is that arithmetic,
    // done once, off in a file a test can interrogate at chosen times. Fixed rather than
    // scrolling, because a sky that scrolls away is a rectangle. Recomputed per fetch: a page
    // left open across dusk catches up on its next refresh, which the tick already schedules.
    // Eased rather than swapped: when the answer changes — a refresh at dusk, a place picked on
    // the other side of the planet — the whole top of the page crossfades instead of flicking,
    // which is the difference between weather moving and a poster changing.
    val today = forecast?.days?.firstOrNull()
    val moment = remember(forecast?.fetched, forecast?.now?.day) {
        SkyMoment.of(
            now = java.time.LocalDateTime.now(),
            sunrise = today?.sunrise,
            sunset = today?.sunset,
            day = forecast?.now?.day != false,
        )
    }
    val sky by androidx.compose.animation.animateColorAsState(
        targetValue = skyColour(scheme, moment),
        animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
        label = "sky",
    )
    // It runs from the very top of the window, behind the app bar, which is transparent over this
    // screen for exactly that reason. Starting it under the bar instead put a hard horizontal
    // line across the screen with a square corner at each end — the only edge on a page where
    // everything else is a rounded card, and the thing that made it look stuck on.
    // A band behind the hero, gone before the cards begin. The full-height version of this — a
    // wash down the whole page with translucent cards over it — looked like an idea and read as
    // mush: colour under everything means contrast under nothing. The sky gets the top of the
    // page; the content gets a page.
    val density = LocalDensity.current
    val reach = with(density) { (padding.calculateTopPadding() + SkyHeight).toPx() }
    val wash = remember(sky, reach) {
        Brush.verticalGradient(
            0f to sky.copy(alpha = 0.55f),
            0.55f to sky.copy(alpha = 0.20f),
            1f to sky.copy(alpha = 0f),
            endY = reach,
        )
    }

    Box(Modifier.fillMaxSize().background(wash)) {
        // The weather itself, moving, under everything else. It is drawn over the same band the
        // wash covers and fades out with it, so the page below stays a page.
        forecast?.takeIf { model.settings.liveSky }?.let {
            // How hard it is falling right now, when the minute-cast knows; negative for "ask
            // the category". Read once per fetch, like the moment above.
            val quarter = remember(it.fetched) { it.falling(java.time.LocalDateTime.now()) }
            // The hand's tilt: the sky is drawn through a camera, and the camera listens to
            // the accelerometer, so tipping the phone slides the layers by their own depths.
            val tilt by rememberTilt(enabled = true)
            // And the rain lands in the hand: the lightest tick there is, at rain's own
            // irregular rhythm, harder rain closing the gaps. Frame-driven, so it stops when
            // the screen does — background, battery saver, animations off.
            val raining = it.now.sky in WET_SKIES
            RainPulse(
                active = raining && model.settings.hapticRain,
                intensity = quarter?.rain?.let { mm -> (mm / 2.5).coerceIn(0.15, 1.0).toFloat() }
                    ?: when (it.now.sky) {
                        Sky.DRIZZLE -> 0.2f
                        Sky.SHOWERS -> 0.75f
                        Sky.THUNDER -> 0.7f
                        Sky.SLEET -> 0.35f
                        else -> 0.5f
                    },
            )
            LiveSky(
                sky = it.now.sky,
                day = it.now.day,
                // The rain leans the way the wind is actually blowing and as hard as it is
                // actually blowing, which makes the picture one more reading rather than one
                // more ornament. The sun and the moon sit where the day has actually got to,
                // and the drops fall as thick as the minute-cast says they are falling.
                windKmh = it.now.wind,
                windFrom = it.now.direction,
                daylight = moment.daylight,
                night = moment.night,
                moonPhase = moment.moonPhase,
                glow = moment.glow,
                haze = sky,
                tilt = tilt,
                rainMm = quarter?.rain ?: -1.0,
                snowCm = quarter?.snow ?: -1.0,
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
                            SunArc(today.sunrise, today.sunset)
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
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Modifier.arrive(slot: Int, fetched: Long, ledger: MutableSet<Int>): Modifier {
    val played = slot in ledger
    val progress = remember(fetched, slot) {
        androidx.compose.animation.core.Animatable(if (played) 1f else 0f)
    }
    val entrance = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    LaunchedEffect(fetched, slot) {
        if (!played) {
            kotlinx.coroutines.delay(slot * 70L)
            progress.animateTo(1f, entrance)
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

/** The skies where something is falling that a hand should feel. */
private val WET_SKIES = setOf(Sky.DRIZZLE, Sky.RAIN, Sky.SHOWERS, Sky.THUNDER, Sky.SLEET)

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

    // The number walks to a new value rather than being restamped: first composition lands on
    // the answer at once, and after that a refresh that moves the temperature — or a switch to
    // Fahrenheit, which moves it further — counts there, so a change is something you can see
    // happen instead of something you have to notice happened.
    val degrees = units.degrees.from(forecast.now.temperature)
    val counted = remember { androidx.compose.animation.core.Animatable(degrees.toFloat()) }
    val settleSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    LaunchedEffect(degrees) {
        counted.animateTo(degrees.toFloat(), settleSpec)
    }

    Column(Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = counted.value.roundToInt().toString() + "°",
                // The number is the reason the app was opened, and it is set like it: large,
                // light and in plain ink. It had a gradient in it for one release; type wearing
                // an effect is an effect first and a number second, and everything else this
                // screen shed went for the same reason.
                style = MaterialTheme.typography.displayLarge.merge(
                    androidx.compose.ui.text.TextStyle(
                        fontSize = 88.sp,
                        lineHeight = 92.sp,
                        fontWeight = FontWeight.Light,
                        letterSpacing = (-1).sp,
                    ),
                ),
                maxLines = 1,
            )
            Spacer(Modifier.width(18.dp))
            Icon(
                painter = painterResource(forecast.now.sky.icon(forecast.now.day)),
                contentDescription = sky,
                tint = skyInk(forecast.now.sky, scheme),
                modifier = Modifier.size(56.dp),
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
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "↑ " + write(units, today.high),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "↓ " + write(units, today.low),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
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

/**
 * One cell of the slab: its mark, its words, its number — and where it stands.
 *
 * [fill] is the reading as a fraction of its own everyday range, and it is what turns a cell from
 * a caption into an instrument: the ring around the mark is at a glance what the number is on a
 * second look. [turn] is the wind's bearing, worn by the needle.
 */
private data class Meter(
    val icon: Int,
    val label: String,
    val value: String,
    val turn: Float? = null,
    val fill: Float? = null,
)

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
                fill = (today?.rain ?: 0) / 100f,
            ),
        )
        if (now.humidity >= 0) {
            add(
                Meter(
                    R.drawable.wx_humidity,
                    stringResource(R.string.wx_humidity),
                    "${now.humidity}%",
                    fill = now.humidity / 100f,
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
                // Fifty km/h is a day everybody calls windy; the ring is full there.
                fill = (now.wind / 50.0).toFloat(),
            ),
        )
        if (now.gust >= 0) {
            add(
                Meter(
                    R.drawable.wx_gust,
                    stringResource(R.string.wx_gust),
                    "${units.wind.from(now.gust).roundToInt()} " +
                        stringResource(windLabel(units.wind)),
                    fill = (now.gust / 70.0).toFloat(),
                ),
            )
        }
        if (now.uv >= 0) {
            add(
                Meter(
                    R.drawable.wx_uv,
                    stringResource(R.string.wx_uv),
                    "${now.uv.roundToInt()}",
                    // Eleven is the top of the published scale.
                    fill = (now.uv / 11.0).toFloat(),
                ),
            )
        }
        if (now.pressure >= 0) {
            add(
                Meter(
                    R.drawable.wx_pressure,
                    stringResource(R.string.wx_pressure),
                    "${units.pressure.from(now.pressure).roundToInt()} " +
                        stringResource(pressureLabel(units.pressure)),
                    // The band nearly all surface weather lives in, centred on 1010 hPa.
                    fill = ((now.pressure - 980.0) / 60.0).toFloat(),
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
                        meter,
                        shape = cellShape(
                            firstRow = line == 0,
                            lastRow = line == rows.size - 1,
                            firstCol = column == 0,
                            lastCol = column == row.size - 1,
                            slab = MaterialTheme.shapes.largeIncreased,
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

/**
 * Which corners of a cell belong to the slab, from where the cell sits in it.
 *
 * The slab's own corners are the theme's shape role, not a number, so the group's outline is
 * whatever Expressive says a large container is this year; only the inner seams are ours.
 */
private fun cellShape(
    firstRow: Boolean,
    lastRow: Boolean,
    firstCol: Boolean,
    lastCol: Boolean,
    slab: androidx.compose.foundation.shape.CornerBasedShape,
): androidx.compose.foundation.shape.CornerBasedShape = RoundedCornerShape(
    topStart = if (firstRow && firstCol) slab.topStart else CornerSize(CellCorner),
    topEnd = if (firstRow && lastCol) slab.topEnd else CornerSize(CellCorner),
    bottomStart = if (lastRow && firstCol) slab.bottomStart else CornerSize(CellCorner),
    bottomEnd = if (lastRow && lastCol) slab.bottomEnd else CornerSize(CellCorner),
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
    shape: androidx.compose.ui.graphics.Shape,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = shape,
        modifier = modifier.fillMaxHeight(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 6.dp),
        ) {
            MeterDial(meter)
            Spacer(Modifier.height(8.dp))
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

/**
 * The mark in its ring: the reading drawn before it is read.
 *
 * The track is the metric's everyday range and the lit arc is where today stands in it, so the
 * slab answers "how windy, how wet, how hard is the sun" at a glance and keeps the numbers for
 * the second look. The arc starts at twelve and runs clockwise, because that is where every dial
 * anyone has ever read starts.
 */
@Composable
private fun MeterDial(meter: Meter) {
    val scheme = MaterialTheme.colorScheme
    val track = scheme.outlineVariant.copy(alpha = 0.45f)
    val lit = scheme.primary
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(44.dp)) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val stroke = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 3.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
            val inset = stroke.width / 2f
            val bounds = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2)
            drawArc(
                color = track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = bounds,
                style = stroke,
            )
            meter.fill?.let { fill ->
                drawArc(
                    color = lit,
                    startAngle = -90f,
                    sweepAngle = 360f * fill.coerceIn(0.02f, 1f),
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = bounds,
                    style = stroke,
                )
            }
        }
        Icon(
            painter = painterResource(meter.icon),
            contentDescription = null,
            tint = scheme.primary,
            modifier = Modifier
                .size(18.dp)
                .then(meter.turn?.let { Modifier.rotate(it) } ?: Modifier),
        )
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
        shape = MaterialTheme.shapes.largeIncreased,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Gutter)
            .testTag(BLOCK),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
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
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "lean",
    )

    Column(
        // Today wears the same tonal pill the hour strip's "Now" does, so the two cards point at
        // the present the same way. The pill is inset from the card's edge and the row's content
        // keeps its old inset in total, so the columns still line up with the rows around them.
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
            .clip(MaterialTheme.shapes.large)
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
            tint = skyInk(day.sky, scheme),
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
            enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
            exit = shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
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
    // A pill rather than a floating line: the one sentence on the page that is about the app
    // instead of the weather, set the way this design sets asides everywhere else.
    Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.Center) {
        Text(
            text = stringResource(R.string.wx_updated, stamp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f))
                .padding(horizontal = 12.dp, vertical = 5.dp),
        )
    }
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
