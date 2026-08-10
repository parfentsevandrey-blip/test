package app.quire.calendar.m3

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import app.quire.calendar.core.DayLoad
import app.quire.calendar.core.MonthModel
import java.time.LocalDate
import java.time.YearMonth

/**
 * The month grid: seven columns, six rows, every row the same height whatever the month, so the
 * geometry never shifts underneath a swipe.
 *
 * Everything is a Material role rather than a colour: today is a filled `primary` circle, the
 * selected day a `secondaryContainer` one, marks take `tertiary` unless the event's own calendar
 * colour is available and wanted. That is what makes the grid follow the wallpaper on Android 12
 * and up without a single colour of its own.
 */
@Composable
fun MonthGrid(
    month: YearMonth,
    cells: List<LocalDate>,
    weekdayLabels: List<String>,
    weekdayOrder: List<java.time.DayOfWeek>,
    today: LocalDate,
    selected: LocalDate,
    loads: Map<LocalDate, DayLoad>,
    settings: CalendarModel.Settings,
    onPick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = rememberLocale()
    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            if (settings.weekNumbers) Spacer(Modifier.size(WeekNumberWidth))
            weekdayLabels.forEachIndexed { index, label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    color = if (settings.dimWeekends && MonthModel.isWeekend(weekdayOrder[index])) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.weight(1f).padding(vertical = 6.dp),
                )
            }
        }

        for (row in 0 until MonthModel.ROWS) {
            Row(modifier = Modifier.fillMaxWidth()) {
                if (settings.weekNumbers) {
                    Text(
                        text = MonthModel.weekOfYear(
                            cells[row * MonthModel.COLUMNS],
                            locale,
                        ).toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.size(WeekNumberWidth, CellHeight)
                            .padding(top = 14.dp),
                    )
                }
                for (column in 0 until MonthModel.COLUMNS) {
                    val date = cells[row * MonthModel.COLUMNS + column]
                    DayCell(
                        date = date,
                        month = month,
                        today = today,
                        selected = selected,
                        load = loads[date],
                        settings = settings,
                        onPick = onPick,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    month: YearMonth,
    today: LocalDate,
    selected: LocalDate,
    load: DayLoad?,
    settings: CalendarModel.Settings,
    onPick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val inMonth = YearMonth.from(date) == month
    val isToday = date == today
    val isSelected = date == selected
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme

    // Today wins the filled treatment; a selection elsewhere gets the quieter container. Both are
    // Material roles, so the pair stays legible whatever the wallpaper turns them into.
    val disc = when {
        isToday -> scheme.primary
        isSelected -> scheme.secondaryContainer
        else -> Color.Transparent
    }
    val onDisc = when {
        isToday -> scheme.onPrimary
        isSelected -> scheme.onSecondaryContainer
        !inMonth -> scheme.onSurfaceVariant.copy(alpha = 0.38f)
        settings.dimWeekends && MonthModel.isWeekend(date.dayOfWeek) -> scheme.onSurfaceVariant
        else -> scheme.onSurface
    }

    val count = load?.count ?: 0
    // The density tint is the surface stepping up rather than a colour of its own, so a busy day
    // reads as raised paper instead of a stain.
    val ground = if (settings.density && count > 0 && !isToday && !isSelected) {
        scheme.surfaceContainerHighest.copy(alpha = (0.25f + 0.15f * count).coerceAtMost(0.9f))
    } else {
        Color.Transparent
    }

    // The disc grows into the cell it was tapped in rather than appearing there, on the theme's
    // spatial spring — which overshoots slightly, so the selection lands with a little weight.
    // Colour crosses on an effects spec instead: a colour that overshoots is a colour that goes
    // somewhere it was never asked to be.
    val marked = isToday || isSelected
    val discScale by animateFloatAsState(
        targetValue = if (marked) 1f else 0.5f,
        animationSpec = motion.defaultSpatialSpec(),
        label = "disc",
    )
    val discAlpha by animateFloatAsState(
        targetValue = if (marked) 1f else 0f,
        animationSpec = motion.fastEffectsSpec(),
        label = "discAlpha",
    )
    val discColour by animateColorAsState(disc, motion.defaultEffectsSpec(), label = "discColour")
    val inkColour by animateColorAsState(onDisc, motion.defaultEffectsSpec(), label = "ink")
    val groundColour by animateColorAsState(ground, motion.slowEffectsSpec(), label = "ground")

    // A day is a small target with no edges of its own, so the ripple alone is easy to miss.
    // Pressing it shrinks it under the finger and it springs back on release.
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val press by animateFloatAsState(
        targetValue = if (pressed) PRESSED_SCALE else 1f,
        animationSpec = motion.fastSpatialSpec(),
        label = "press",
    )
    val haptics = LocalHapticFeedback.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .height(CellHeight)
            .padding(2.dp)
            .graphicsLayer {
                scaleX = press
                scaleY = press
            }
            .clip(MaterialTheme.shapes.medium)
            .background(groundColour)
            .clickable(
                enabled = inMonth || settings.showAdjacent,
                interactionSource = interactions,
                indication = ripple(),
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                onPick(date)
            },
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(DiscSize)) {
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        scaleX = discScale
                        scaleY = discScale
                        alpha = discAlpha
                    }
                    .clip(CircleShape)
                    .background(discColour),
            )
            if (inMonth || settings.showAdjacent) {
                Text(
                    text = date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = inkColour,
                )
            }
        }
        Spacer(Modifier.height(3.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            if (inMonth || settings.showAdjacent) {
                val colours = load?.colours ?: IntArray(0)
                val shown = minOf(count, 3)
                // Marks arrive when a month's events do, which is a frame or two after the grid
                // itself. Growing them in is what keeps that from reading as a glitch. They are
                // hidden rather than scaled to nothing so an absent mark takes no width — one dot
                // has to sit under the middle of its date, not to the left of it.
                repeat(3) { index ->
                    val mark = if (settings.colouredMarks && index < colours.size) {
                        Color(colours[index])
                    } else if (isToday) {
                        scheme.onPrimary
                    } else {
                        scheme.tertiary
                    }
                    AnimatedVisibility(
                        visible = index < shown,
                        enter = scaleIn(motion.defaultSpatialSpec()) +
                            fadeIn(motion.fastEffectsSpec()),
                        exit = scaleOut(motion.fastSpatialSpec()) +
                            fadeOut(motion.fastEffectsSpec()),
                    ) {
                        Box(Modifier.size(MarkSize).clip(CircleShape).background(mark))
                    }
                }
            }
        }
    }
}

/**
 * A month small enough that twelve fit on a screen: the year view's tile. It carries the day
 * numbers rather than a heat block, because a year you cannot read the dates in is a picture of
 * a year rather than one.
 */
@Composable
fun MiniMonth(
    month: YearMonth,
    cells: List<LocalDate>,
    weekdayInitials: List<String>,
    today: LocalDate,
    loads: Map<LocalDate, DayLoad>,
    dayFont: TextUnit,
    nameFont: TextUnit,
    disc: Dp,
    onOpen: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val locale = rememberLocale()
    val isThisMonth = YearMonth.from(today) == month
    // A card, the same as the month it opens into. Twelve blocks of numbers on bare page have
    // nothing saying where one month stops and the next starts except the gap between them, and
    // the transform into the full month then grows a rectangle out of nothing.
    Card(
        onClick = { onOpen(month) },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (isThisMonth) {
                scheme.secondaryContainer.copy(alpha = 0.45f)
            } else {
                scheme.surfaceContainerLow
            },
        ),
        // Flat. Twelve resting shadows is twelve extra layers to compose, and they are all being
        // animated at once during the transform into a month — which is where the year showed it.
        // The colour already says the tile is a tile; the shadow was only saying it again slower.
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.padding(3.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 5.dp)) {
            Text(
                text = MonthModel.monthName(month, locale),
                style = MaterialTheme.typography.titleSmall,
                fontSize = nameFont,
                color = if (isThisMonth) scheme.primary else scheme.onSurface,
                maxLines = 1,
                modifier = Modifier.padding(bottom = 2.dp),
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                weekdayInitials.forEach { initial ->
                    Text(
                        text = initial,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = dayFont,
                        color = scheme.outline,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            // The weeks take whatever height the tile has left, so a year fills its page instead of
            // sitting in a band at the top of one.
            Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                for (row in 0 until MonthModel.ROWS) {
                    Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        for (column in 0 until MonthModel.COLUMNS) {
                            val date = cells[row * MonthModel.COLUMNS + column]
                            val inMonth = YearMonth.from(date) == month
                            val isToday = date == today
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(disc)
                                        .clip(CircleShape)
                                        .background(
                                            if (isToday) scheme.primary else Color.Transparent,
                                        ),
                                ) {
                                    Text(
                                        text = if (inMonth) date.dayOfMonth.toString() else "",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = dayFont,
                                        lineHeight = dayFont,
                                        color = when {
                                            isToday -> scheme.onPrimary
                                            loads[date] != null -> scheme.onSurface
                                            else -> scheme.onSurfaceVariant
                                        },
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        softWrap = false,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private val CellHeight = 56.dp
private val DiscSize = 34.dp
private val MarkSize = 4.dp
private val WeekNumberWidth = 24.dp

/** How far a day sinks while it is held. */
private const val PRESSED_SCALE = 0.90f
