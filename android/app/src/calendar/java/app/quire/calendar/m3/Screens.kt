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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.util.lerp
import kotlin.math.abs
import kotlinx.coroutines.flow.drop
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    onCreate: (LocalDate) -> Unit = {},
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
                // The month is a card rather than ink on the page. It used to be the latter, with
                // a rule under it to keep it off the agenda — and a full-width hairline is how a
                // list separates two rows, not how a page separates two things. A card says the
                // same thing by being an object: the grid has edges, the agenda is what is under
                // it, and nothing has to be drawn to announce that.
                Card(
                    // The theme's own role for the largest container, not a number: 28 today,
                    // whatever Expressive says tomorrow.
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 4.dp)
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
                ) {
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
                        onCompose = onCreate,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
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

    // The clock, read once a minute rather than once per composition. "In 40 min" that was true
    // when the screen opened and has said so ever since is worse than no figure at all, and a
    // minute is the resolution the phrase is written to anyway.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(MINUTE_MILLIS)
            now = System.currentTimeMillis()
        }
    }

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
        // Named where a name is what people use. "Monday, August 10, 2026" is correct and nobody
        // says it about today; the full date stays for every other day, where it is the only way
        // to know which one you are looking at.
        val written = remember(day.date, locale) {
            DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale).format(day.date)
        }
        val heading = when (day.date) {
            LocalDate.now() -> stringResource(R.string.today)
            LocalDate.now().plusDays(1) -> stringResource(R.string.tomorrow)
            LocalDate.now().minusDays(1) -> stringResource(R.string.yesterday)
            else -> written
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.fillMaxWidth().padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = 16.dp,
                        bottom = 4.dp,
                    ),
                ) {
                    Text(text = heading, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f))
                    // How many, so the day has a shape before any of it is read. Left off when
                    // there is nothing, because "0 entries" and the empty state below it are the
                    // same sentence twice.
                    if (day.entries.isNotEmpty()) {
                        Text(
                            text = pluralStringResource(
                                R.plurals.agenda_count,
                                day.entries.size,
                                day.entries.size,
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (day.entries.isEmpty()) {
                item {
                    // Loading and empty look alike from the outside, so they are told apart here:
                    // the expressive indicator while the provider is being asked, the sentence
                    // only once it has answered.
                    if (day.loading) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                        ) {
                            LoadingIndicator()
                        }
                    } else {
                        // Centred under an outline of the thing that is empty, rather than a
                        // sentence hung on the left margin of a page with nothing else on it.
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.EventAvailable,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.size(44.dp),
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = stringResource(R.string.nothing_scheduled),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                // Keyed, so switching days reflows the list instead of restamping it: a row
                // that survives the switch slides to its new place on the theme's spring, and
                // rows that come and go fade — which is what makes flicking through a week feel
                // like moving through it rather than reloading it.
                items(
                    day.entries,
                    // A recurring event carries one eventId across every occurrence, so the key
                    // is the occurrence: the event at its start.
                    key = { entry -> entry.eventId to entry.begin },
                ) { entry ->
                    AgendaRow(entry, now, onOpenEvent, Modifier.animateItem())
                }
            }
        }
    }
}

/**
 * One entry, and where it stands relative to the clock.
 *
 * A calendar's real question is not "what is on today" but "what is next", and the times alone
 * make that arithmetic the reader's job. So an entry under way says so, one coming up soon says
 * how soon, and one that has finished steps back rather than sitting in the list at full strength
 * competing with the ones that have not.
 */
@Composable
private fun AgendaRow(
    entry: AgendaEntry,
    now: Long,
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
    val detail = listOfNotNull(
        times ?: stringResource(R.string.all_day),
        entry.location?.takeIf { it.isNotBlank() } ?: entry.calendarName,
    ).joinToString(" · ")

    val running = !entry.allDay && now in entry.begin until entry.end
    val over = !entry.allDay && now >= entry.end
    val soon = (entry.begin - now).takeIf { !entry.allDay && it > 0 && it < SOON_MILLIS }

    // A card rather than a list row. An entry is a thing you can pick up and open, and the flat
    // row it used to be — a stripe and two lines of text straight on the page — read as a caption
    // under the grid rather than as something with a tap target.
    // The card dips under the finger and springs back, and the open lands with a click the hand
    // can feel: the two together are what make it an object rather than a picture of one.
    val pressing = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    Card(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
            onOpen(entry)
        },
        interactionSource = pressing,
        colors = CardDefaults.cardColors(
            containerColor = if (running) scheme.secondaryContainer else scheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .springPress(pressing)
            // Finished, not gone. It stays where it was — a day you can no longer see the start
            // of is a day that has been edited behind your back — but it stops competing.
            .alpha(if (over) 0.55f else 1f),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Box(
                Modifier
                    .width(4.dp)
                    .height(38.dp)
                    .clip(CircleShape)
                    .background(
                        if (entry.colour != 0) Color(entry.colour) else scheme.tertiary,
                    ),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = entry.title.ifBlank { stringResource(R.string.untitled_event) },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (running || soon != null) {
                Spacer(Modifier.width(10.dp))
                Countdown(running, soon)
            }
        }
    }
}

/** "Now", or how long until then, as a pill at the end of the row it belongs to. */
@Composable
private fun Countdown(running: Boolean, soon: Long?) {
    val scheme = MaterialTheme.colorScheme
    // Rounded, not truncated: forty-four minutes and fifty seconds is "in 45 min" to
    // anybody reading it, and "in 44 min" to nobody.
    val minutes = (((soon ?: 0L) + 30_000L) / 60_000L).toInt()
    Text(
        text = when {
            running -> stringResource(R.string.entry_now)
            // Under an hour it is a number of minutes, because that is the resolution the
            // decision is made at; over one it is hours, because "in 154 min" is arithmetic.
            minutes < 60 -> stringResource(R.string.entry_in_minutes, minutes.coerceAtLeast(1))
            else -> stringResource(R.string.entry_in_hours, minutes / 60)
        },
        style = MaterialTheme.typography.labelMedium,
        color = if (running) scheme.onPrimary else scheme.onSurfaceVariant,
        maxLines = 1,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (running) scheme.primary else scheme.surfaceContainerHighest)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** How far ahead an entry is still worth counting down to rather than simply listing. */
private const val SOON_MILLIS = 6L * 60L * 60L * 1000L
private const val MINUTE_MILLIS = 60_000L

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
    val density = LocalDensity.current
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val available = maxHeight - padding.calculateTopPadding() -
            padding.calculateBottomPadding() - YearGridPadding * 2
        val tile = (available / YearRows).coerceAtLeast(MinimumYearTile)

        // The type is sized to the tile rather than the other way round.
        //
        // A day cell in the year is about a seventh of a third of the screen — sixteen points on a
        // phone — and two digits of `labelSmall` are wider than that before the font scale is
        // touched at all. Turned up, the numbers ran into each other: "12131415161718". So the
        // size is derived from the cell in *pixels* and converted back through the current scale,
        // which pins the drawn width to the space there is. It is capped at the style's own size,
        // so this can only ever make the year smaller than the theme asked for, never larger, and
        // the month view a tap away honours the scale in full.
        val column = (maxWidth - YearGridPadding * 2) / YearColumns - MiniMonthInsets
        val cell = column / 7
        val rows = tile - MiniMonthHeader
        val dayFont = with(density) {
            minOf(cell.toPx() * 0.62f, rows.toPx() / 9f, MiniMonthMaxDay.toPx()).toSp()
        }
        // The month's name gets the same treatment for the same reason: "September" is nine
        // characters, and at a turned-up scale it ran off the end of its own tile.
        val nameFont = with(density) {
            minOf(column.toPx() / 5.5f, MiniMonthMaxName.toPx()).toSp()
        }
        val disc = minOf(cell * 0.92f, MiniDiscMax)

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
                    dayFont = dayFont,
                    nameFont = nameFont,
                    disc = disc,
                    onOpen = onOpenMonth,
                )
            }
        }
    }
}

private const val YearColumns = 3
private const val YearRows = 4
private val YearGridPadding = 8.dp

/** What a tile spends on its own margins and padding before the seven columns start. */
private val MiniMonthInsets = 18.dp

/** What a tile spends above the weeks: the month's name and the row of initials. */
private val MiniMonthHeader = 40.dp

/** The year never sets its days larger than this, whatever room it turns out to have. */
private val MiniMonthMaxDay = 11.sp
private val MiniMonthMaxName = 14.sp
private val MiniDiscMax = 18.dp

/** Below this a month's dates stop being readable, so the year scrolls rather than shrinking. */
private val MinimumYearTile = 118.dp

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
    val scheme = MaterialTheme.colorScheme
    if (model.query.trim().length < 2) {
        Nothing(Icons.Outlined.Search, stringResource(R.string.search_empty))
        return
    }
    if (model.results.isEmpty()) {
        Nothing(Icons.Outlined.SearchOff, stringResource(R.string.search_none))
        return
    }
    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        // Keyed like the agenda, so narrowing a query reflows the survivors instead of
        // restamping the list from the top.
        items(model.results, key = { it.eventId to it.begin }) { entry ->
            val date = remember(entry) { EventRepository.dateOf(entry) }
            // The same card as an agenda entry, because it is the same thing found a different
            // way. A result that looks unlike the row it takes you to is two designs for one
            // object.
            val pressing = remember { MutableInteractionSource() }
            Card(
                onClick = { onPick(date) },
                interactionSource = pressing,
                colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .springPress(pressing),
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        text = entry.title.ifBlank { stringResource(R.string.untitled_event) },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = formatter.format(date),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** An empty state: the outline of the missing thing, and one line about it, both centred. */
@Composable
private fun Nothing(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(top = 48.dp, start = 32.dp, end = 32.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.size(44.dp),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
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
