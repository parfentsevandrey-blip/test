package app.rosa.calendar.ui.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.SizeF
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.rosa.calendar.R
import app.rosa.calendar.ui.CalendarScreen
import app.rosa.calendar.ui.LocalTabBarInset
import app.rosa.calendar.ui.settings.Label
import app.rosa.calendar.ui.settings.Section
import app.rosa.calendar.weather.SharedWeather
import app.rosa.calendar.widget.CalendarWidgetProvider
import app.rosa.calendar.widget.CalendarWidgetUpdater
import app.rosa.calendar.widget.calendarContent
import app.rosa.calendar.widget.studio.CalendarPreview
import app.rosa.calendar.widget.studio.CalendarStudioActivity
import app.rosa.weather.core.data.repository.WidgetConfigRepository
import app.rosa.weather.core.designsystem.component.GlassButton
import app.rosa.weather.core.designsystem.component.RosaIcon
import app.rosa.weather.core.designsystem.component.RosaIconView
import app.rosa.weather.core.designsystem.haptics.LocalHaptics
import app.rosa.weather.core.designsystem.theme.Rosa
import app.rosa.weather.core.model.Place
import app.rosa.weather.core.model.WidgetConfig
import app.rosa.weather.widget.provider.WidgetSizes
import app.rosa.weather.widget.render.WidgetContent
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class PlacedCalendar(val id: Int, val config: WidgetConfig, val size: SizeF?, val content: WidgetContent)

data class WidgetTabState(val placed: List<PlacedCalendar>, val fresh: WidgetContent)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WidgetTabViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    configs: WidgetConfigRepository,
    weather: SharedWeather,
) : ViewModel() {
    private val tick = MutableStateFlow(0)

    val state: StateFlow<WidgetTabState?> = combine(configs.all, weather.changes, tick) { stored, _, _ -> stored }
        .mapLatest { stored ->
            val manager = AppWidgetManager.getInstance(context)
            val ids = CalendarWidgetUpdater.allWidgetIds(context)
            val placedConfigs = ids.map { it to (stored.byId[it] ?: CalendarWidgetUpdater.DEFAULT_CONFIG) }
            val snapshot = weather.snapshot(placedConfigs.map { it.second.placeId } + Place.FOLLOW_APP_ID)
            val now = System.currentTimeMillis() / 1000
            val placed = placedConfigs.map { (id, config) ->
                val size = runCatching { WidgetSizes.from(manager.getAppWidgetOptions(id), manager.getAppWidgetInfo(id)).first() }.getOrNull()
                PlacedCalendar(id, config, size, calendarContent(config, snapshot, now))
            }
            WidgetTabState(placed, calendarContent(CalendarWidgetUpdater.DEFAULT_CONFIG, snapshot, now))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Reads the placed widgets again, e.g. after the launcher's pin dialog. */
    fun refresh() = tick.update { it + 1 }
}

/**
 * The calendar on the home screen: what a new one looks like, live, and the way to put it there;
 * then the ones already placed, each opening its studio.
 */
@Composable
fun WidgetRoute(viewModel: WidgetTabViewModel = hiltViewModel()) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    WidgetScreen(state)
}

@Composable
fun WidgetScreen(state: WidgetTabState?) {
    val context = LocalContext.current
    val haptics = LocalHaptics.current
    var unsupported by remember { mutableStateOf(false) }
    CalendarScreen(stringResource(R.string.widget_title)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = LocalTabBarInset.current + 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Section(stringResource(R.string.widget_new_look)) {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val w = maxWidth
                    if (state != null) {
                        CalendarPreview(
                            CalendarWidgetUpdater.DEFAULT_CONFIG,
                            state.fresh,
                            Modifier.width(w).height(w * 0.92f),
                            cornerRadiusDp = 26f,
                            live = true,
                        )
                    } else {
                        Spacer(Modifier.width(w).height(w * 0.92f))
                    }
                }
                GlassButton(
                    onClick = {
                        haptics?.confirm()
                        unsupported = !pin(context)
                    },
                    prominent = true,
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RosaIconView(RosaIcon.Plus, Rosa.colors.ink, size = 16.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.widget_add), style = Rosa.type.headline, color = Rosa.colors.ink)
                    }
                }
                if (unsupported) Label(stringResource(R.string.widget_pin_unsupported))
            }
            Section(stringResource(R.string.widget_placed)) {
                val placed = state?.placed.orEmpty()
                if (placed.isEmpty()) {
                    Label(stringResource(R.string.widget_none))
                } else {
                    Label(stringResource(R.string.widget_edit_hint))
                    placed.forEach { widget -> PlacedRow(widget) }
                }
            }
        }
    }
}

@Composable
private fun PlacedRow(widget: PlacedCalendar) {
    val context = LocalContext.current
    val haptics = LocalHaptics.current
    // Shown at its own proportions, scaled to fit the row.
    val size = widget.size ?: SizeF(320f, 300f)
    val scale = minOf(1f, 150f / size.height, 260f / size.width)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(remember { MutableInteractionSource() }, null, role = Role.Button) {
                haptics?.tick()
                context.startActivity(
                    Intent(context, CalendarStudioActivity::class.java)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widget.id)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CalendarPreview(
            widget.config,
            widget.content,
            Modifier.size((size.width * scale).dp, (size.height * scale).dp),
            cornerRadiusDp = 20f,
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.widget_size, size.width.roundToInt(), size.height.roundToInt()) + " dp", style = Rosa.type.label, color = Rosa.colors.inkSoft)
            Text(stringResource(R.string.widget_change), style = Rosa.type.headline, color = Rosa.colors.ink)
        }
        RosaIconView(RosaIcon.Chevron, Rosa.colors.inkSoft, size = 18.dp)
    }
}

/** Asks the launcher to put a calendar on the home screen; false when it can't. */
private fun pin(context: Context): Boolean {
    val manager = AppWidgetManager.getInstance(context) ?: return false
    if (!manager.isRequestPinAppWidgetSupported) return false
    return runCatching { manager.requestPinAppWidget(ComponentName(context, CalendarWidgetProvider::class.java), null, null) }.getOrDefault(false)
}
