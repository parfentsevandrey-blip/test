package app.quire.calendar.m3

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.util.lerp
import kotlin.math.abs
import kotlinx.coroutines.flow.drop
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.quire.R
import app.quire.calendar.core.AgendaEntry
import app.quire.calendar.core.EventRepository
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** How far either way the month pager reaches, and where "now" sits inside it. */
private const val PAGE_COUNT = 2401
private const val PAGE_ORIGIN = PAGE_COUNT / 2

/**
 * The main screen: a month you can swipe through, and the chosen day's entries under it.
 *
 * The pager is the whole navigation between months — no arrows, because a swipe is what a
 * calendar on a phone is for — and the app bar names whichever page it settles on.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MonthScreen(
    model: CalendarModel,
    padding: PaddingValues,
    onOpenEvent: (AgendaEntry) -> Unit,
    onGrant: () -> Unit,
    shared: SharedTransitionScope? = null,
    visibility: AnimatedVisibilityScope? = null,
) {
    val anchor = remember { YearMonth.now() }
    val state = rememberPagerState(
        initialPage = PAGE_ORIGIN + monthsBetween(anchor, model.month),
        pageCount = { PAGE_COUNT },
    )

    val haptics = LocalHapticFeedback.current

    // The pager is the source of truth for which month is showing; the model follows it rather
    // than the two trying to drive each other into a loop.
    LaunchedEffect(state) {
        snapshotFlow { state.currentPage }.collect { page ->
            model.showMonth(anchor.plusMonths((page - PAGE_ORIGIN).toLong()))
        }
    }

    // A settled page is worth a tick: a swipe that lands between two months and springs back to
    // the one it came from otherwise feels identical to one that carried.
    LaunchedEffect(state) {
        snapshotFlow { state.settledPage }.drop(1).collect {
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        }
    }

    // Pulling down asks the provider again. A calendar that syncs in the background has no moment
    // the user can point at where it became stale, so the gesture that means "are you sure?"
    // everywhere else means it here too.
    val refresh = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = model.refreshing,
        onRefresh = {
            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
            model.refresh()
        },
        state = refresh,
        modifier = Modifier.fillMaxSize().padding(padding),
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                state = refresh,
                isRefreshing = model.refreshing,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = !model.hasPermission,
                enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                    fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                exit = shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                    fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
            ) {
                PermissionCard(onGrant)
            }
            HorizontalPager(state = state, modifier = Modifier.fillMaxWidth()) { page ->
                val month = anchor.plusMonths((page - PAGE_ORIGIN).toLong())
                // How far this page is from settled, as a fraction of a screen. The month being swiped
                // away sinks and fades a little as it goes, so a half-finished swipe reads as one
                // month passing behind another rather than as two grids sliding on the same plane.
                val offset = ((state.currentPage - page) + state.currentPageOffsetFraction)
                    .coerceIn(-1f, 1f)
                MonthGrid(
                    month = month,
                    cells = model.cells(month),
                    weekdayLabels = model.weekdayLabels(),
                    weekdayOrder = model.weekdayOrder(),
                    today = model.today,
                    selected = model.selected,
                    loads = model.loads[month].orEmpty(),
                    settings = model.settings,
                    onPick = { model.openDay(it) },
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        // Only the settled month is the shared element. A page still sliding past has
                        // no business claiming the bounds the year tile is growing into.
                        .then(sharedMonth(shared, visibility, month, page == state.currentPage))
                        .graphicsLayer {
                            val away = abs(offset)
                            alpha = lerp(1f, 0.35f, away)
                            val shrink = lerp(1f, 0.90f, away)
                            scaleX = shrink
                            scaleY = shrink
                        },
                )
            }
            HorizontalDivider(Modifier.padding(top = 8.dp))
            AgendaList(
                date = model.selected,
                entries = model.agenda,
                loading = model.agendaLoading,
                onOpenEvent = onOpenEvent,
            )
        }
    }
}

@Composable
private fun PermissionCard(onGrant: () -> Unit) {
    OutlinedCard(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.permission_headline),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.permission_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(onClick = onGrant) {
                Text(stringResource(R.string.permission_action))
            }
        }
    }
}

/** One day's worth of agenda, as a value, so an outgoing transition keeps showing its own day. */
private data class Day(
    val date: LocalDate,
    val entries: List<AgendaEntry>,
    val loading: Boolean,
)

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AgendaList(
    date: LocalDate,
    entries: List<AgendaEntry>,
    loading: Boolean,
    onOpenEvent: (AgendaEntry) -> Unit,
) {
    val locale = rememberLocale()
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val quick = MaterialTheme.motionScheme.fastEffectsSpec<Float>()

    // The whole day travels rather than the heading alone — Material's shared axis, along the one
    // the dates themselves lie on: a later day arrives from the right, an earlier one from the
    // left. The day is passed in as one value so the copy on its way out keeps its own entries
    // instead of being repainted with the new day's.
    AnimatedContent(
        targetState = Day(date, entries, loading),
        transitionSpec = {
            val forward = targetState.date > initialState.date
            (
                slideInHorizontally(spatial) { width -> if (forward) width / 4 else -width / 4 } +
                    fadeIn(quick)
                ) togetherWith (
                slideOutHorizontally(spatial) { width -> if (forward) -width / 4 else width / 4 } +
                    fadeOut(quick)
                )
        },
        modifier = Modifier.fillMaxSize(),
        label = "day",
    ) { day ->
        val heading = remember(day.date, locale) {
            DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale).format(day.date)
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                Text(
                    text = heading,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = 16.dp,
                        bottom = 4.dp,
                    ),
                )
            }
            if (day.entries.isEmpty()) {
                item {
                    // Loading and empty look alike from the outside, so they are told apart here:
                    // the expressive indicator while the provider is being asked, the sentence
                    // only once it has answered.
                    if (day.loading) {
                        LoadingIndicator(modifier = Modifier.padding(16.dp))
                    } else {
                        Text(
                            text = stringResource(R.string.nothing_scheduled),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            } else {
                items(day.entries) { entry -> AgendaRow(entry, onOpenEvent) }
            }
        }
    }
}

@Composable
private fun AgendaRow(
    entry: AgendaEntry,
    onOpen: (AgendaEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val zone = remember { ZoneId.systemDefault() }
    val times = remember(entry) {
        if (entry.allDay) {
            null
        } else {
            val fmt = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
            val from = Instant.ofEpochMilli(entry.begin).atZone(zone).toLocalTime()
            val to = Instant.ofEpochMilli(entry.end).atZone(zone).toLocalTime()
            "${fmt.format(from)} – ${fmt.format(to)}"
        }
    }
    ListItem(
        onClick = { onOpen(entry) },
        modifier = modifier,
        supportingContent = {
            val detail = listOfNotNull(
                times ?: stringResource(R.string.all_day),
                entry.location?.takeIf { it.isNotBlank() } ?: entry.calendarName,
            ).joinToString(" · ")
            Text(detail)
        },
        leadingContent = {
            Box(
                Modifier
                    .width(4.dp)
                    .height(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (entry.colour != 0) Color(entry.colour) else scheme.tertiary,
                    ),
            )
        },
    ) {
        Text(entry.title.ifBlank { stringResource(R.string.nothing_scheduled) })
    }
}

/**
 * The whole year, three across and four down, every date legible.
 *
 * The twelve tiles are sized to fill the page rather than to fit their contents: a year that ends
 * half way down the screen reads as a list that ran out, and this is meant to read as a year you
 * can see all of at once. Below the height where that stays legible the grid scrolls instead.
 */
@Composable
fun YearScreen(
    model: CalendarModel,
    padding: PaddingValues,
    shared: SharedTransitionScope? = null,
    visibility: AnimatedVisibilityScope? = null,
    onOpenMonth: (YearMonth) -> Unit,
) {
    val year = model.month.year
    val months = remember(year) { (1..12).map { YearMonth.of(year, it) } }
    val initials = model.weekdayLabels().map { it.take(1) }
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val available = maxHeight - padding.calculateTopPadding() -
            padding.calculateBottomPadding() - YearGridPadding * 2
        val tile = (available / YearRows).coerceAtLeast(MinimumYearTile)
        LazyVerticalGrid(
            columns = GridCells.Fixed(YearColumns),
            contentPadding = PaddingValues(
                start = YearGridPadding,
                end = YearGridPadding,
                top = padding.calculateTopPadding() + YearGridPadding,
                bottom = padding.calculateBottomPadding() + YearGridPadding,
            ),
            modifier = Modifier.fillMaxSize(),
        ) {
            gridItems(months) { month ->
                LaunchedEffect(month) { model.request(month) }
                MiniMonth(
                    month = month,
                    modifier = Modifier.height(tile)
                        .then(sharedMonth(shared, visibility, month)),
                    cells = model.cells(month),
                    weekdayInitials = initials,
                    today = model.today,
                    loads = model.loads[month].orEmpty(),
                    onOpen = onOpenMonth,
                )
            }
        }
    }
}

private const val YearColumns = 3
private const val YearRows = 4
private val YearGridPadding = 8.dp

/** Below this a month's dates stop being readable, so the year scrolls rather than shrinking. */
private val MinimumYearTile = 150.dp

/** Search results, straight into the day they were found in. */
@Composable
fun SearchResults(
    model: CalendarModel,
    onPick: (LocalDate) -> Unit,
) {
    val locale = rememberLocale()
    val formatter = remember(locale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    }
    if (model.query.trim().length < 2) {
        Text(
            text = stringResource(R.string.search_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
        return
    }
    if (model.results.isEmpty()) {
        Text(
            text = stringResource(R.string.search_none),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
        return
    }
    LazyColumn {
        items(model.results) { entry ->
            val date = remember(entry) { EventRepository.dateOf(entry) }
            ListItem(
                onClick = { onPick(date) },
                supportingContent = { Text(formatter.format(date)) },
            ) {
                Text(entry.title.ifBlank { "—" })
            }
        }
    }
}

/**
 * Everything the app can be told.
 *
 * The switches are grouped rather than listed: Android's own settings from 16 onwards draw a run
 * of related rows as one connected block with the outer corners rounded and the inner ones
 * squared off, which is what `segmentedShapes` computes from a row's position in its group. It
 * reads as a handful of decisions instead of a wall of them.
 */
@Composable
fun SettingsScreen(model: CalendarModel, padding: PaddingValues) {
    val settings = model.settings
    LazyColumn(contentPadding = padding, modifier = Modifier.fillMaxSize()) {
        item { SectionHeading(stringResource(R.string.section_look)) }
        item {
            SettingGroup {
                SettingRow(
                    index = 0,
                    count = 1,
                    title = stringResource(R.string.dynamic_colour),
                    hint = stringResource(R.string.dynamic_colour_hint),
                    checked = settings.dynamic,
                    onChange = { model.update(settings.copy(dynamic = it)) },
                )
            }
        }
        item {
            ChoiceRow(
                title = stringResource(R.string.mode),
                options = listOf(
                    stringResource(R.string.mode_auto),
                    stringResource(R.string.mode_light),
                    stringResource(R.string.mode_dark),
                ),
                selected = when (settings.dark) {
                    null -> 0
                    false -> 1
                    true -> 2
                },
                onSelect = {
                    model.update(
                        settings.copy(dark = when (it) { 0 -> null; 1 -> false; else -> true }),
                    )
                },
            )
        }

        item { SectionHeading(stringResource(R.string.section_week)) }
        item {
            val keys = listOf("auto", "mon", "sat", "sun")
            ChoiceRow(
                title = stringResource(R.string.first_day),
                options = listOf(
                    stringResource(R.string.first_day_auto),
                    stringResource(R.string.first_day_mon),
                    stringResource(R.string.first_day_sat),
                    stringResource(R.string.first_day_sun),
                ),
                selected = keys.indexOf(settings.firstDay).coerceAtLeast(0),
                onSelect = { model.update(settings.copy(firstDay = keys[it])) },
            )
        }

        item { SectionHeading(stringResource(R.string.section_grid)) }
        item {
            val rows = listOf(
                Triple(R.string.show_adjacent, R.string.show_adjacent_hint, settings.showAdjacent),
                Triple(R.string.dim_weekends, R.string.dim_weekends_hint, settings.dimWeekends),
                Triple(R.string.week_numbers, R.string.week_numbers_hint, settings.weekNumbers),
                Triple(R.string.coloured_dots, R.string.coloured_dots_hint, settings.colouredMarks),
                Triple(R.string.heat, R.string.heat_hint, settings.density),
            )
            SettingGroup {
                rows.forEachIndexed { index, (title, hint, checked) ->
                    SettingRow(
                        index = index,
                        count = rows.size,
                        title = stringResource(title),
                        hint = stringResource(hint),
                        checked = checked,
                        onChange = { on ->
                            model.update(
                                when (index) {
                                    0 -> settings.copy(showAdjacent = on)
                                    1 -> settings.copy(dimWeekends = on)
                                    2 -> settings.copy(weekNumbers = on)
                                    3 -> settings.copy(colouredMarks = on)
                                    else -> settings.copy(density = on)
                                },
                            )
                        },
                    )
                }
            }
        }

        if (model.calendars.isNotEmpty()) {
            item { SectionHeading(stringResource(R.string.section_calendars)) }
            item {
                SettingGroup {
                    model.calendars.forEachIndexed { index, source ->
                        SettingRow(
                            index = index,
                            count = model.calendars.size,
                            title = source.displayName,
                            hint = source.accountName.takeIf { it != source.displayName },
                            checked = source.id !in settings.hidden,
                            tint = if (source.colour != 0) Color(source.colour) else null,
                            onChange = { on ->
                                val next = if (on) {
                                    settings.hidden - source.id
                                } else {
                                    settings.hidden + source.id
                                }
                                model.update(settings.copy(hidden = next))
                            },
                        )
                    }
                }
            }
        }

        item { SectionHeading(stringResource(R.string.section_about)) }
        item {
            Text(
                text = stringResource(R.string.about_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun ChoiceRow(
    title: String,
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, label ->
                SegmentedButton(
                    selected = index == selected,
                    onClick = { onSelect(index) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                ) {
                    Text(label, maxLines = 1)
                }
            }
        }
    }
}

private fun monthsBetween(from: YearMonth, to: YearMonth): Int =
    ((to.year - from.year) * 12 + (to.monthValue - from.monthValue))
        .coerceIn(-PAGE_ORIGIN, PAGE_ORIGIN)

/**
 * Marks a grid as the same month at both sizes, so the year's tile and the full month are one
 * thing moving rather than two things swapping.
 *
 * Both scopes are optional because these screens are also composed on their own — by a test, or a
 * preview — where there is no transition to belong to, and a shared element outside a
 * [SharedTransitionScope] is an error rather than a no-op.
 *
 * [active] is separate from [month] on purpose: the pager holds three months at a time and only
 * the settled one may claim the bounds, but the state is remembered for all of them either way,
 * so a swipe does not add and remove a `remember` on every frame.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun sharedMonth(
    shared: SharedTransitionScope?,
    visibility: AnimatedVisibilityScope?,
    month: YearMonth,
    active: Boolean = true,
): Modifier {
    if (shared == null || visibility == null) return Modifier
    return with(shared) {
        val state = rememberSharedContentState(key = "month-$month")
        if (!active) {
            Modifier
        } else {
            Modifier.sharedBounds(
                sharedContentState = state,
                animatedVisibilityScope = visibility,
                resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
            )
        }
    }
}
