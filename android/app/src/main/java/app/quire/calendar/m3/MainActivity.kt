package app.quire.calendar.m3

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Intent
import android.os.Bundle
import android.provider.CalendarContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.AppBarWithSearch
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import app.quire.calendar.R
import app.quire.calendar.core.AgendaEntry
import app.quire.calendar.core.MonthModel
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

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
                } else {
                    LargeFlexibleTopAppBar(
                        title = {
                            Text(
                                when (destination) {
                                    Destination.YEAR -> model.month.year.toString()
                                    Destination.SETTINGS -> stringResource(R.string.settings)
                                    else -> MonthModel.monthName(model.month, locale)
                                },
                            )
                        },
                        subtitle = {
                            if (destination == Destination.MONTH) {
                                Text(model.month.year.toString())
                            }
                        },
                        scrollBehavior = scrollBehavior,
                    )
                }
            },
            bottomBar = {
                // The short bar is Expressive's own: a shorter band, and a selection pill that
                // grows around the icon on the same spring the rest of the theme moves on.
                ShortNavigationBar {
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
                if (destination == Destination.MONTH) {
                    FloatingActionButton(onClick = { activity?.compose(model.selected) }) {
                        Icon(Icons.Default.Add, stringResource(R.string.new_event))
                    }
                }
            },
        ) { padding ->
            when (destination) {
                Destination.MONTH -> MonthScreen(
                    model = model,
                    padding = padding,
                    onOpenEvent = { activity?.open(it) },
                    onGrant = { requestCalendar.launch(Manifest.permission.READ_CALENDAR) },
                )
                Destination.YEAR -> YearScreen(model, padding) { month ->
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

private fun ComponentActivity.open(entry: AgendaEntry) {
    val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, entry.eventId)
    val intent = Intent(Intent.ACTION_VIEW)
        .setData(uri)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, entry.begin)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, entry.end)
    runCatching { startActivity(intent) }
        .onFailure { if (it !is ActivityNotFoundException) throw it }
}
