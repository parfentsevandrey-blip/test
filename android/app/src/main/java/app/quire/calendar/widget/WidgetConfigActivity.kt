package app.quire.calendar.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.quire.calendar.R
import app.quire.calendar.core.Accent
import app.quire.calendar.core.Prefs
import app.quire.calendar.core.Skin
import app.quire.calendar.m3.QuireTheme
import app.quire.calendar.m3.SettingGroup
import app.quire.calendar.m3.SettingRow
import kotlin.math.roundToInt

/**
 * The screen the launcher shows while a widget is being placed.
 *
 * It is Compose and Material like the rest of the app now, but it configures a RemoteViews widget,
 * which cannot itself be Compose — so every change here ends in a repaint of the real widget
 * rather than in a preview of one.
 */
class WidgetConfigActivity : ComponentActivity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        // Cancelled unless Done is pressed, which is what the launcher expects of a configure
        // activity: a back press must leave no widget behind.
        setResult(Activity.RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))

        setContent {
            val dark = androidx.compose.foundation.isSystemInDarkTheme()
            QuireTheme(dark = dark, dynamic = true) {
                WidgetConfigScreen(widgetId) { done() }
            }
        }
    }

    private fun done() {
        val prefs = Prefs.get(this).widget(widgetId)
        // Writing the skin is what marks the placement configured.
        prefs.skin = prefs.skin
        MonthWidgetProvider.requestUpdate(this)
        setResult(
            Activity.RESULT_OK,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
        )
        finish()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WidgetConfigScreen(widgetId: Int, onDone: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context).widget(widgetId) }

    var skin by remember { mutableStateOf(prefs.skin) }
    var accent by remember { mutableStateOf(prefs.accent) }
    var dynamic by remember { mutableStateOf(prefs.dynamic) }
    var opacity by remember { mutableStateOf(prefs.opacity.toFloat()) }
    var showEvents by remember { mutableStateOf(prefs.showEvents) }
    var weekNumbers by remember { mutableStateOf(prefs.weekNumbers) }

    fun repaint() = MonthWidgetProvider.requestUpdate(context)

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.widget_config_title)) }) },
    ) { padding ->
        LazyColumn(contentPadding = padding, modifier = Modifier.fillMaxSize()) {
            item {
                val skins = listOf(Skin.AUTO, Skin.PAPER, Skin.INK, Skin.COLOUR)
                val labels = listOf(
                    stringResource(R.string.skin_auto),
                    stringResource(R.string.skin_paper),
                    stringResource(R.string.skin_ink),
                    stringResource(R.string.skin_colour),
                )
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.skin), style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(8.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        skins.forEachIndexed { index, value ->
                            SegmentedButton(
                                selected = value == skin,
                                onClick = { skin = value; prefs.skin = value; repaint() },
                                shape = SegmentedButtonDefaults.itemShape(index, skins.size),
                            ) { Text(labels[index], maxLines = 1) }
                        }
                    }
                }
            }

            if (skin == Skin.COLOUR) {
                item {
                    SettingGroup {
                        SettingRow(
                            index = 0,
                            count = 1,
                            title = stringResource(R.string.dynamic_colour),
                            hint = stringResource(R.string.dynamic_colour_hint),
                            checked = dynamic,
                        ) { dynamic = it; prefs.dynamic = it; repaint() }
                    }
                }
            }

            if (!(skin == Skin.COLOUR && dynamic)) {
                item {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                            stringResource(R.string.accent),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Accent.entries.forEach { value ->
                                val chosen = value == accent
                                // A swatch is a colour and nothing else on screen, so its name has
                                // to be carried in semantics or a screen reader announces six
                                // identical unlabelled buttons.
                                val name = stringResource(accentLabel(value))
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (chosen) {
                                                MaterialTheme.colorScheme.secondaryContainer
                                            } else {
                                                Color.Transparent
                                            },
                                        )
                                        .selectable(
                                            selected = chosen,
                                            role = Role.RadioButton,
                                            onClick = {
                                                accent = value; prefs.accent = value; repaint()
                                            },
                                        )
                                        .semantics { contentDescription = name },
                                ) {
                                    Box(
                                        Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(Color(value.light)),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        "${stringResource(R.string.opacity)} · ${opacity.roundToInt()}%",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Slider(
                        value = opacity,
                        onValueChange = { opacity = it },
                        onValueChangeFinished = {
                            prefs.opacity = opacity.roundToInt()
                            repaint()
                        },
                        valueRange = 40f..100f,
                    )
                }
            }

            item {
                SettingGroup {
                    SettingRow(
                        index = 0,
                        count = 2,
                        title = stringResource(R.string.show_events),
                        hint = stringResource(R.string.show_events_hint),
                        checked = showEvents,
                    ) { showEvents = it; prefs.showEvents = it; repaint() }
                    SettingRow(
                        index = 1,
                        count = 2,
                        title = stringResource(R.string.week_numbers),
                        hint = stringResource(R.string.week_numbers_hint),
                        checked = weekNumbers,
                    ) { weekNumbers = it; prefs.weekNumbers = it; repaint() }
                }
            }

            item {
                Button(
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) { Text(stringResource(R.string.done)) }
            }
        }
    }
}

/** The accent's own name, for anyone who cannot see which swatch is which. */
private fun accentLabel(accent: Accent): Int = when (accent) {
    Accent.CINNABAR -> R.string.accent_cinnabar
    Accent.INDIGO -> R.string.accent_indigo
    Accent.MOSS -> R.string.accent_moss
    Accent.OCHRE -> R.string.accent_ochre
    Accent.PLUM -> R.string.accent_plum
    Accent.GRAPHITE -> R.string.accent_graphite
}
