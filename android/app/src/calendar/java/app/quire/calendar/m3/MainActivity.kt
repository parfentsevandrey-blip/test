package app.quire.calendar.m3

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Intent
import android.os.Bundle
import android.provider.CalendarContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.AppBarWithSearch
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import app.quire.R
import app.quire.calendar.core.AgendaEntry
import app.quire.calendar.core.MonthModel
import java.time.LocalDate
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * The whole app, as one Material 3 Expressive screen with a navigation bar under it.
 *
 * Nothing here is drawn by hand any more: the bar, the app bar, the search field, the switches
 * and the segmented rows are Material's own components, so they answer the device's colour
 * scheme, its font scale and its accessibility settings without this app having an opinion.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { QuireApp(intent) }
    }
}

private enum class Destination { MONTH, YEAR, SEARCH, SETTINGS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuireApp(intent: Intent?) {
    val model: CalendarModel = viewModel()
    val activity = LocalActivity.current as? ComponentActivity

    val dark = when (model.settings.dark) {
        true -> true
        false -> false
        null -> androidx.compose.foundation.isSystemInDarkTheme()
    }

    var destination by rememberSaveable { mutableStateOf(Destination.MONTH) }
    val haptics = LocalHapticFeedback.current
    var showing by remember { mutableStateOf<AgendaEntry?>(null) }
    var jumping by rememberSaveable { mutableStateOf(false) }

    val requestCalendar = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) model.permissionGranted() }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        model.refresh()
        intent?.data?.takeIf { it.scheme == "quire" }?.lastPathSegment?.let { segment ->
            runCatching { LocalDate.parse(segment) }.getOrNull()?.let { model.openDay(it) }
        }
    }

    QuireTheme(dark = dark, dynamic = model.settings.dynamic) {
        val scrollBehavior =
            TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
        val locale = rememberLocale()

        Scaffold(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                if (destination == Destination.SEARCH) {
                    QuireSearchBar(model)
                } else if (destination == Destination.YEAR) {
                    // A compact bar on the year, and only there. The large one spends about a
                    // fifth of the screen on four digits, and this is the one screen whose whole
                    // claim is that a year fits on it — with the tall bar it did not, and a year
                    // you have to scroll is a list of months.
                    TopAppBar(
                        title = {
                            RollingLabel(
                                text = model.month.year.toString(),
                                order = model.month.year,
                            )
                        },
                    )
                } else {
                    LargeFlexibleTopAppBar(
                        title = {
                            // The title is the one label that changes on every swipe, so it
                            // travels the way the months did: up for a later month, down for an
                            // earlier one. Anything else and the bar reads as a caption on a
                            // screen that moved without it.
                            RollingLabel(
                                text = when (destination) {
                                    Destination.YEAR -> model.month.year.toString()
                                    Destination.SETTINGS -> stringResource(R.string.settings)
                                    else -> MonthModel.monthName(model.month, locale)
                                },
                                order = model.month.year * 12 + model.month.monthValue,
                            )
                        },
                        subtitle = {
                            if (destination == Destination.MONTH) {
                                RollingLabel(
                                    text = model.month.year.toString(),
                                    order = model.month.year,
                                )
                            }
                        },
                        scrollBehavior = scrollBehavior,
                    )
                }
            },
            bottomBar = {
                // The short bar is Expressive's own: a shorter band, and a selection pill that
                // grows around the icon on the same spring the rest of the theme moves on.
                //
                // It can also be scrubbed: press anywhere on it and slide, and the selection
                // follows the finger — the pill hops from item to item on its spring, the screen
                // fades through behind it, and each crossing ticks. A tap still taps; the drag
                // only claims the gesture once it has moved past touch slop, at which point the
                // item under the finger loses its press instead of firing.
                val order = remember {
                    listOf(
                        Destination.MONTH, Destination.YEAR,
                        Destination.SEARCH, Destination.SETTINGS,
                    )
                }
                val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current ==
                    androidx.compose.ui.unit.LayoutDirection.Rtl
                var barWidth by remember { androidx.compose.runtime.mutableIntStateOf(0) }

                fun under(x: Float): Destination {
                    if (barWidth <= 0) return destination
                    val slot = (x / (barWidth / order.size))
                        .toInt()
                        .coerceIn(0, order.lastIndex)
                    return order[if (rtl) order.lastIndex - slot else slot]
                }

                fun scrubTo(x: Float) {
                    val landed = under(x)
                    if (landed != destination) {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        destination = landed
                    }
                }

                ShortNavigationBar(
                    modifier = Modifier
                        .onSizeChanged { barWidth = it.width }
                        .pointerInput(order) {
                            detectHorizontalDragGestures(
                                // The press point counts too: a scrub that starts on Search and
                                // ends on Settings should pass through Search on the way.
                                onDragStart = { start -> scrubTo(start.x) },
                                onHorizontalDrag = { change, _ ->
                                    change.consume()
                                    scrubTo(change.position.x)
                                },
                            )
                        },
                ) {
                    ShortNavigationBarItem(
                        selected = destination == Destination.MONTH,
                        onClick = {
                            if (destination == Destination.MONTH) model.goToToday()
                            destination = Destination.MONTH
                        },
                        icon = { Icon(Icons.Default.Today, null) },
                        label = { Text(stringResource(R.string.today)) },
                    )
                    ShortNavigationBarItem(
                        selected = destination == Destination.YEAR,
                        onClick = { destination = Destination.YEAR },
                        icon = { Icon(Icons.Default.CalendarMonth, null) },
                        label = { Text(stringResource(R.string.year)) },
                    )
                    ShortNavigationBarItem(
                        selected = destination == Destination.SEARCH,
                        onClick = { destination = Destination.SEARCH },
                        icon = { Icon(Icons.Default.Search, null) },
                        label = { Text(stringResource(R.string.search)) },
                    )
                    ShortNavigationBarItem(
                        selected = destination == Destination.SETTINGS,
                        onClick = { destination = Destination.SETTINGS },
                        icon = { Icon(Icons.Default.Settings, null) },
                        label = { Text(stringResource(R.string.settings)) },
                    )
                }
            },
            floatingActionButton = {
                AnimatedVisibility(
                    visible = destination == Destination.MONTH,
                    enter = scaleIn(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                        fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
                    exit = scaleOut(MaterialTheme.motionScheme.fastSpatialSpec()) +
                        fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                ) {
                    QuireFabMenu(
                        onNew = { activity?.compose(model.selected) },
                        onJump = { jumping = true },
                        onToday = { model.goToToday() },
                    )
                }
            },
        ) { padding ->
            // Back returns to the month before it leaves the app, and it does it under the
            // finger: the gesture's own progress shrinks and slides the screen you are leaving,
            // so a back you change your mind about springs back instead of committing.
            var backProgress by remember { mutableFloatStateOf(0f) }
            PredictiveBackHandler(enabled = destination != Destination.MONTH) { events ->
                try {
                    events.collect { backProgress = it.progress }
                    destination = Destination.MONTH
                } finally {
                    backProgress = 0f
                }
            }
            val retreat by animateFloatAsState(
                targetValue = backProgress,
                animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                label = "back",
            )

            // Material's fade-through between destinations: the outgoing screen fades, the
            // incoming one fades and grows the last tenth of its size. Nothing slides, because
            // these four are siblings rather than a stack.
            // The specs are read here rather than inside the transition, because a transition
            // spec is an ordinary lambda: it cannot reach the theme once it is running.
            val arriving = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
            val appearing = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
            val leaving = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
            // The year and the month are the same twelve months at two sizes, so going between
            // them is a container transform rather than a cut: the tile you tapped keeps its
            // place on screen and grows into the grid. Everything else still fades through.
            SharedTransitionLayout {
                AnimatedContent(
                    targetState = destination,
                    transitionSpec = {
                        (
                            fadeIn(appearing) +
                                scaleIn(arriving, initialScale = FADE_THROUGH_SCALE)
                            ) togetherWith fadeOut(leaving)
                    },
                    modifier = Modifier.graphicsLayer {
                        val shrink = 1f - BACK_SHRINK * retreat
                        scaleX = shrink
                        scaleY = shrink
                        alpha = 1f - BACK_FADE * retreat
                    },
                    label = "destination",
                ) { current ->
                    when (current) {
                        Destination.MONTH -> MonthScreen(
                            model = model,
                            padding = padding,
                            onOpenEvent = { showing = it },
                            onGrant = {
                                requestCalendar.launch(Manifest.permission.READ_CALENDAR)
                            },
                            onCreate = { day -> activity?.compose(day) },
                            shared = this@SharedTransitionLayout,
                            visibility = this@AnimatedContent,
                        )
                        Destination.YEAR -> YearScreen(
                            model = model,
                            padding = padding,
                            shared = this@SharedTransitionLayout,
                            visibility = this@AnimatedContent,
                        ) { month ->
                            model.showMonth(month)
                            model.openDay(month.atDay(1))
                            destination = Destination.MONTH
                        }
                        Destination.SEARCH -> Box(Modifier.fillMaxSize().padding(padding)) {
                            SearchResults(model) { date ->
                                model.openDay(date)
                                destination = Destination.MONTH
                            }
                        }
                        Destination.SETTINGS -> SettingsScreen(model, padding)
                    }
                }
            }
        }

        showing?.let { entry ->
            EventSheet(
                entry = entry,
                onOpen = { showing = null; activity?.open(it) },
                onShare = { showing = null; activity?.share(it, locale) },
                onDismiss = { showing = null },
            )
        }

        if (jumping) {
            JumpToDate(
                from = model.selected,
                onPick = { date ->
                    jumping = false
                    model.openDay(date)
                    destination = Destination.MONTH
                },
                onDismiss = { jumping = false },
            )
        }
    }
}

/**
 * The expressive floating action button: one button that opens into the three things worth doing
 * from a month, rather than three buttons competing for the same corner.
 *
 * The toggle carries its own progress, which is what lets the plus rotate into a close as the menu
 * comes out instead of swapping icons half way.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun QuireFabMenu(onNew: () -> Unit, onJump: () -> Unit, onToday: () -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    FloatingActionButtonMenu(
        expanded = open,
        button = {
            ToggleFloatingActionButton(
                checked = open,
                onCheckedChange = {
                    open = it
                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                },
            ) {
                // The plus turns into a close as the menu comes out, driven by the toggle's own
                // progress rather than swapped at the half-way point.
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.actions),
                    modifier = with(ToggleFloatingActionButtonDefaults) {
                        Modifier.animateIcon({ checkedProgress })
                    },
                )
            }
        },
    ) {
        FloatingActionButtonMenuItem(
            onClick = { open = false; onNew() },
            icon = { Icon(Icons.Default.Add, null) },
            text = { Text(stringResource(R.string.new_event)) },
        )
        FloatingActionButtonMenuItem(
            onClick = { open = false; onJump() },
            icon = { Icon(Icons.Default.CalendarMonth, null) },
            text = { Text(stringResource(R.string.jump)) },
        )
        FloatingActionButtonMenuItem(
            onClick = { open = false; onToday() },
            icon = { Icon(Icons.Default.Today, null) },
            text = { Text(stringResource(R.string.today)) },
        )
    }
}

/** Material's own date picker, for the months a swipe would take all afternoon to reach. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JumpToDate(from: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val zone = ZoneId.systemDefault()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = from.atStartOfDay(zone).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = state.selectedDateMillis
                    if (millis == null) {
                        onDismiss()
                    } else {
                        // The picker answers in UTC midnight, which is the day before in any
                        // zone west of Greenwich if it is read as a local instant.
                        onPick(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                },
            ) { Text(stringResource(R.string.jump_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    ) {
        DatePicker(state = state, title = { Text(stringResource(R.string.jump), Modifier.padding(24.dp)) })
    }
}

/** How small an arriving screen starts, as Material's fade-through has it. */
private const val FADE_THROUGH_SCALE = 0.92f

/** How far a back gesture pushes the screen away before it commits. */
private const val BACK_SHRINK = 0.10f
private const val BACK_FADE = 0.35f

/**
 * The search field, as the app bar rather than as a box floating over one.
 *
 * `AppBarWithSearch` takes the whole top slot and hands the query over as a `TextFieldState`, which is
 * the current shape of the API: the field owns its own text and the model only ever hears about it
 * changing. Results are the screen underneath, so what you type filters what you are looking at
 * rather than covering it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuireSearchBar(model: CalendarModel) {
    val field = rememberTextFieldState(model.query)
    val state = rememberSearchBarState()

    LaunchedEffect(field) {
        snapshotFlow { field.text.toString() }.collect { model.search(it) }
    }

    AppBarWithSearch(
        state = state,
        inputField = {
            SearchBarDefaults.InputField(
                textFieldState = field,
                searchBarState = state,
                onSearch = { model.search(it) },
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, null) },
            )
        },
    )
}

/** Composing and opening events is the calendar app's job; Quire only ever reads. */
private fun ComponentActivity.compose(date: LocalDate) {
    val start = if (date == LocalDate.now()) {
        System.currentTimeMillis()
    } else {
        date.atTime(LocalTime.of(9, 0)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
    val intent = Intent(Intent.ACTION_INSERT)
        .setData(CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, start + 3_600_000L)
    runCatching { startActivity(intent) }
        .onFailure { if (it !is ActivityNotFoundException) throw it }
}

/**
 * Passes an entry on as text.
 *
 * Text rather than a calendar file, because the receiver is as likely to be a chat as a calendar,
 * and a line somebody can read beats an attachment they have to open. The times are formatted in
 * the reader's own locale for the same reason.
 */
private fun ComponentActivity.share(entry: AgendaEntry, locale: Locale) {
    val zone = ZoneId.systemDefault()
    val day = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)
    val clock = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
    val from = Instant.ofEpochMilli(entry.begin).atZone(zone)
    val to = Instant.ofEpochMilli(entry.end).atZone(zone)

    val when_ = if (entry.allDay) {
        day.format(from)
    } else {
        "${day.format(from)}, ${clock.format(from)} – ${clock.format(to)}"
    }
    val text = listOfNotNull(
        entry.title.takeIf { it.isNotBlank() },
        when_,
        entry.location?.takeIf { it.isNotBlank() },
    ).joinToString("\n")

    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, entry.title)
        .putExtra(Intent.EXTRA_TEXT, text)
    runCatching { startActivity(Intent.createChooser(send, getString(R.string.share_via))) }
        .onFailure { if (it !is ActivityNotFoundException) throw it }
}

private fun ComponentActivity.open(entry: AgendaEntry) {
    val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, entry.eventId)
    val intent = Intent(Intent.ACTION_VIEW)
        .setData(uri)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, entry.begin)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, entry.end)
    runCatching { startActivity(intent) }
        .onFailure { if (it !is ActivityNotFoundException) throw it }
}
