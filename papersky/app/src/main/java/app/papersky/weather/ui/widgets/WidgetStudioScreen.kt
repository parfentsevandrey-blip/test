package app.papersky.weather.ui.widgets

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.papersky.weather.AppContainer
import app.papersky.weather.R
import app.papersky.weather.core.model.MotionLevel
import app.papersky.weather.design.Paper
import app.papersky.weather.design.PaperButton
import app.papersky.weather.design.PaperCard
import app.papersky.weather.design.laidDown
import app.papersky.weather.design.rememberHaptics
import app.papersky.weather.scene.SceneState
import app.papersky.weather.ui.common.PaperPage
import app.papersky.weather.ui.common.SectionTitle
import app.papersky.weather.widget.PaperskyWidget
import app.papersky.weather.widget.PaperskyWidgetReceiver
import app.papersky.weather.widget.PinnedWidgetReceiver
import app.papersky.weather.widget.WidgetConfig
import app.papersky.weather.widget.WidgetDirectory
import app.papersky.weather.widget.WidgetPreset
import app.papersky.weather.widget.config.Grid
import app.papersky.weather.widget.config.WidgetPreview
import app.papersky.weather.widget.config.presetName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class PlacedWidget(val appWidgetId: Int, val config: WidgetConfig, val size: DpSize)

class WidgetStudioViewModel(private val c: AppContainer, private val app: android.app.Application) : ViewModel() {
    private val _placed = MutableStateFlow<List<PlacedWidget>>(emptyList())
    val placed: StateFlow<List<PlacedWidget>> = _placed

    fun reload() {
        viewModelScope.launch {
            val manager = GlanceAppWidgetManager(app)
            _placed.value = WidgetDirectory.installed(app).map { (id, config) ->
                val sizes = runCatching { manager.getAppWidgetSizes(id) }.getOrDefault(emptyList())
                val size = sizes.maxByOrNull { it.width.value * it.height.value }?.takeIf { it.width.value > 0 } ?: Grid.size(4, 2)
                PlacedWidget(manager.getAppWidgetId(id), config, size)
            }
        }
    }

    /** Asks the launcher to place a widget; the chosen preset is applied once it lands. */
    fun pin(preset: WidgetPreset, onUnsupported: () -> Unit) {
        viewModelScope.launch {
            val callback = PendingIntent.getBroadcast(
                app, preset.ordinal,
                Intent(app, PinnedWidgetReceiver::class.java).putExtra(PinnedWidgetReceiver.EXTRA_PRESET, preset.name),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            val ok = GlanceAppWidgetManager(app).requestPinGlanceAppWidget(
                PaperskyWidgetReceiver::class.java,
                preview = PaperskyWidget(),
                previewState = preset.config.toPreferences(),
                successCallback = callback,
            )
            if (!ok) onUnsupported()
        }
    }
}

@Composable
fun WidgetStudioScreen(vm: WidgetStudioViewModel, scene: SceneState, motion: MotionLevel, village: Boolean, onEdit: (Int) -> Unit, onBack: () -> Unit) {
    val placed by vm.placed.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val h = rememberHaptics()
    LifecycleResumeEffect(Unit) {
        vm.reload()
        onPauseOrDispose { }
    }
    PaperPage(stringResource(R.string.widgets_title), scene, motion, onBack, village = village) {
        item("intro") {
            BasicText(stringResource(R.string.widgets_intro), Modifier.padding(horizontal = 4.dp), style = Paper.type.hand.copy(color = Paper.colors.paperInk))
        }
        if (placed.isNotEmpty()) {
            item("placed-title") { SectionTitle(stringResource(R.string.widgets_on_home)) }
            placed.forEachIndexed { i, w ->
                item("w-${w.appWidgetId}") {
                    PaperCard(Modifier.laidDown(i), seed = w.appWidgetId, onClick = { onEdit(w.appWidgetId) }) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            val shown = DpSize(w.size.width.coerceAtMost(320.dp), w.size.height.coerceAtMost(260.dp))
                            WidgetPreview(w.config, shown)
                        }
                        Spacer(Modifier.height(10.dp))
                        BasicText(stringResource(R.string.widgets_tap_to_edit), style = Paper.type.caption.copy(color = Paper.colors.paperInkSoft))
                    }
                }
            }
        }
        item("presets-title") { SectionTitle(stringResource(R.string.widgets_add)) }
        WidgetPreset.entries.forEach { preset ->
            item("p-${preset.name}") {
                PaperCard(Modifier.laidDown(1 + preset.ordinal), seed = preset.ordinal * 13 + 5, tilt = if (preset.ordinal % 2 == 0) -0.5f else 0.5f) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            BasicText(presetName(preset), style = Paper.type.heading.copy(color = Paper.colors.paperInk))
                            BasicText("${preset.cols} × ${preset.rows}", style = Paper.type.caption.copy(color = Paper.colors.paperInkSoft))
                        }
                        PaperButton(stringResource(R.string.widgets_place), {
                            h.confirm()
                            vm.pin(preset) { android.widget.Toast.makeText(context, R.string.widgets_unsupported, android.widget.Toast.LENGTH_LONG).show() }
                        }, primary = false)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.Center) {
                        WidgetPreview(preset.config, Grid.size(preset.cols, preset.rows).let { DpSize(it.width.coerceAtMost(310.dp), it.height) })
                    }
                }
            }
        }
        item("tip") {
            BasicText(stringResource(R.string.widgets_tip), Modifier.padding(horizontal = 4.dp), style = Paper.type.caption.copy(color = Paper.colors.paperInkSoft))
        }
    }
}
