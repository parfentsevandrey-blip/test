package app.rosa.calendar.ui.month

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.rosa.calendar.data.Occurrence
import app.rosa.calendar.ui.WeatherState
import app.rosa.weather.core.designsystem.component.GlassSurface
import app.rosa.weather.core.designsystem.component.WeatherGlyph
import app.rosa.weather.core.designsystem.format.WeatherFormat
import app.rosa.weather.core.designsystem.glass.GlassStyle
import app.rosa.weather.core.designsystem.haptics.LocalHaptics
import app.rosa.weather.core.designsystem.motion.LocalMotionEnabled
import app.rosa.weather.core.designsystem.motion.RosaMotion
import app.rosa.weather.core.designsystem.theme.Rosa
import app.rosa.weather.core.model.CalendarDay
import app.rosa.weather.core.model.CalendarMonth
import app.rosa.weather.core.model.WeatherCondition
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * A month's six weeks on the glass: each day's number (today in a bead of the painting's colour),
 * the colours of its events, and the forecast's weather on the days still to come. The chosen day
 * sits on a brighter platter that flows to the next one chosen; held and slid, the platter lifts
 * into a lens that follows the finger across the days, ticking at each.
 */
@Composable
internal fun MonthGrid(
    month: YearMonth,
    today: LocalDate,
    selected: LocalDate,
    firstDay: DayOfWeek,
    weekNumbers: Boolean,
    events: Map<LocalDate, List<Occurrence>>,
    weather: WeatherState,
    onSelect: (LocalDate) -> Unit,
) {
    val context = LocalContext.current
    val locale = Locale.getDefault()
    val haptics = LocalHaptics.current
    val motion = LocalMotionEnabled.current
    val density = LocalDensity.current
    val grid = remember(month, today, firstDay) { CalendarMonth.of(month, today, firstDay) }
    val lent = (weather as? WeatherState.Lent)?.weather
    val format = remember(lent?.units, lent?.zone) { lent?.let { WeatherFormat(context, it.units, it.zone) } }
    val showWeather = lent != null && lent.days.keys.any { it in grid.first..grid.last && !it.isBefore(today) }
    val cellHeight: Dp = if (showWeather) 60.dp else 48.dp
    val dayFormat = remember(locale) { DateTimeFormatter.ofPattern("EEEE, d MMMM", locale) }
    val select by rememberUpdatedState(onSelect)

    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp)) {
        // The weekdays' names, the weekend's in the painting's colour.
        Row(Modifier.fillMaxWidth().height(26.dp), verticalAlignment = Alignment.CenterVertically) {
            if (weekNumbers) Spacer(Modifier.width(WeekColumn))
            grid.columns.forEach { day ->
                val weekend = day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY
                Text(
                    day.getDisplayName(TextStyle.SHORT_STANDALONE, locale).trimEnd('.').replaceFirstChar { it.titlecase(locale) },
                    style = Rosa.type.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = if (weekend) Rosa.colors.accent else Rosa.colors.inkSoft,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().height(cellHeight * CalendarMonth.WEEKS)) {
            val weekColumn = if (weekNumbers) WeekColumn else 0.dp
            val cellWidth = (maxWidth - weekColumn) / 7
            val cellW = with(density) { cellWidth.toPx() }
            val cellH = with(density) { cellHeight.toPx() }
            val left = with(density) { weekColumn.toPx() }
            fun cellOf(date: LocalDate): Pair<Int, Int>? {
                grid.weeks.forEachIndexed { row, week ->
                    week.forEachIndexed { col, day -> if (day.date == date) return row to col }
                }
                return null
            }
            fun dateAt(position: Offset): LocalDate {
                val col = ((position.x - left) / cellW).toInt().coerceIn(0, 6)
                val row = (position.y / cellH).toInt().coerceIn(0, CalendarMonth.WEEKS - 1)
                return grid.weeks[row][col].date
            }
            fun topLeftOf(row: Int, col: Int) = Offset(left + col * cellW, row * cellH)

            // The platter under the chosen day: it flows from day to day on the gel spring.
            val cell = cellOf(selected)
            val platter = remember(month) { Animatable(cell?.let { topLeftOf(it.first, it.second) } ?: Offset.Zero, Offset.VectorConverter) }
            LaunchedEffect(cell, cellW, cellH) {
                val target = cell?.let { topLeftOf(it.first, it.second) } ?: return@LaunchedEffect
                if (motion) platter.animateTo(target, RosaMotion.gel()) else platter.snapTo(target)
            }
            // Held and slid: a lens follows the finger.
            var scrubbing by remember { mutableStateOf(false) }
            var finger by remember { mutableStateOf(Offset.Zero) }
            val lift by animateFloatAsState(if (scrubbing) 1f else 0f, spring(0.72f, 520f), label = "lift")
            val platterVisible = cell != null && lift < 0.98f
            if (platterVisible) {
                GlassSurface(
                    Modifier
                        .offset { IntOffset((platter.value.x + Inset.toPx()).roundToInt(), (platter.value.y + Inset.toPx()).roundToInt()) }
                        .size(cellWidth - Inset * 2, cellHeight - Inset * 2)
                        .graphicsLayer { alpha = 1f - lift },
                    style = GlassStyle.Regular,
                    cornerRadius = 16.dp,
                    shadow = false,
                    touchResponsive = false,
                    prominent = true,
                ) {}
            }
            // The days themselves.
            Column(Modifier.fillMaxSize().clearAndSetSemantics { }) {
                grid.weeks.forEachIndexed { row, week ->
                    Row(Modifier.fillMaxWidth().height(cellHeight)) {
                        if (weekNumbers) {
                            Box(Modifier.width(WeekColumn).fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(grid.weekNumbers[row].toString(), style = Rosa.type.caption, color = Rosa.colors.inkFaint)
                            }
                        }
                        week.forEach { day ->
                            DayCell(
                                day = day,
                                today = today,
                                selected = day.date == selected,
                                events = events[day.date].orEmpty(),
                                glyph = if (showWeather && day.inMonth && !day.date.isBefore(today)) lent?.days?.get(day.date) else null,
                                temperature = { celsius -> format?.temperature(celsius).orEmpty() },
                                roomy = cellWidth >= 46.dp,
                                modifier = Modifier.weight(1f).fillMaxSize(),
                            )
                        }
                    }
                }
            }
            if (lift > 0.01f) {
                val lensW = cellWidth + 12.dp
                val lensH = cellHeight + 8.dp
                GlassSurface(
                    Modifier
                        .offset {
                            IntOffset(
                                (finger.x - lensW.toPx() / 2).roundToInt().coerceIn(0, (constraints.maxWidth - lensW.toPx()).roundToInt().coerceAtLeast(0)),
                                (finger.y - lensH.toPx() * 0.72f).roundToInt(),
                            )
                        }
                        .size(lensW, lensH)
                        .graphicsLayer {
                            alpha = lift
                            scaleX = 0.8f + 0.25f * lift
                            scaleY = 0.8f + 0.25f * lift
                        },
                    style = GlassStyle.Lens,
                    cornerRadius = 22.dp,
                    touchResponsive = false,
                ) {}
            }
            // Taps and holds, for the whole grid: a day is where the finger is.
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(grid, cellW, cellH, left) {
                        detectTapGestures { position ->
                            val date = dateAt(position)
                            if (date != selected) haptics?.tick()
                            select(date)
                        }
                    }
                    .pointerInput(grid, cellW, cellH, left) {
                        var last: LocalDate? = null
                        detectDragGesturesAfterLongPress(
                            onDragStart = { position ->
                                scrubbing = true
                                finger = position
                                haptics?.press()
                                last = dateAt(position).also { select(it) }
                            },
                            onDragEnd = { scrubbing = false },
                            onDragCancel = { scrubbing = false },
                        ) { change, _ ->
                            change.consume()
                            finger = change.position
                            val date = dateAt(change.position)
                            if (date != last) {
                                last = date
                                haptics?.tick()
                                select(date)
                            }
                        }
                    },
            )
            // What a screen reader gets: each day as a button.
            Column(Modifier.fillMaxSize()) {
                grid.weeks.forEach { week ->
                    Row(Modifier.fillMaxWidth().height(cellHeight)) {
                        if (weekNumbers) Spacer(Modifier.width(WeekColumn))
                        week.forEach { day ->
                            val count = events[day.date].orEmpty().size
                            Box(
                                Modifier.weight(1f).fillMaxSize().semantics {
                                    role = Role.Button
                                    this.selected = day.date == selected
                                    contentDescription = buildString {
                                        append(dayFormat.format(day.date))
                                        if (count > 0) append(", ").append(count)
                                    }
                                    onClick { select(day.date); true }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A day's square: its number, its events' colours, and the weather the forecast has for it. */
@Composable
private fun DayCell(
    day: CalendarDay,
    today: LocalDate,
    selected: Boolean,
    events: List<Occurrence>,
    glyph: app.rosa.weather.core.model.DailyPoint?,
    temperature: (Double) -> String,
    roomy: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = Rosa.colors
    val past = day.date.isBefore(today)
    val ink = when {
        day.isToday -> Color.White
        !day.inMonth -> colors.inkFaint
        day.isWeekend -> if (past) colors.accent.copy(alpha = 0.62f) else colors.accent
        past -> colors.inkSoft
        else -> colors.ink
    }
    Column(modifier.padding(top = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
            if (day.isToday) TodayBead(colors.accent)
            Text(
                day.date.dayOfMonth.toString(),
                style = Rosa.type.headline.copy(
                    fontSize = 17.sp,
                    fontWeight = if (day.isToday || selected) FontWeight.Bold else if (day.inMonth) FontWeight.SemiBold else FontWeight.Medium,
                ),
                color = ink,
                maxLines = 1,
            )
        }
        // Up to three dots, one per calendar colour.
        val dots = remember(events) { events.map { it.color or 0xFF000000.toInt() }.distinct().take(3) }
        Row(Modifier.height(6.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
            dots.forEach { argb ->
                Canvas(Modifier.size(5.dp)) {
                    drawCircle(Color(argb).copy(alpha = if (day.inMonth) 1f else 0.5f))
                }
            }
        }
        if (glyph != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                WeatherGlyph(
                    WeatherCondition.fromWmo(glyph.weatherCode),
                    isDay = true,
                    modifier = Modifier.size(15.dp),
                )
                if (roomy) {
                    Spacer(Modifier.width(2.dp))
                    Text(temperature(glyph.temperatureMax), style = Rosa.type.caption.copy(fontSize = 10.5.sp), color = colors.inkSoft, maxLines = 1)
                }
            }
        }
    }
}

/** Today: a bead of the painting's colour, lit from above like glass. */
@Composable
private fun TodayBead(accent: Color) {
    Canvas(Modifier.size(32.dp)) {
        val r = size.minDimension / 2f
        drawCircle(Color.Black.copy(alpha = 0.16f), r, center + Offset(0f, 1.6.dp.toPx()))
        drawCircle(accent, r)
        drawCircle(
            Brush.verticalGradient(
                0f to Color.White.copy(alpha = 0.42f),
                0.5f to Color.White.copy(alpha = 0.04f),
                1f to Color.Black.copy(alpha = 0.12f),
            ),
            r,
        )
        drawCircle(
            Brush.radialGradient(listOf(Color.White.copy(alpha = 0.55f), Color.Transparent), center + Offset(-r * 0.35f, -r * 0.45f), r * 0.5f),
            r * 0.5f,
            center + Offset(-r * 0.35f, -r * 0.45f),
        )
    }
}

/** How far the chosen day's platter keeps from its square's edges. */
private val Inset = 3.dp

private val WeekColumn = 24.dp
