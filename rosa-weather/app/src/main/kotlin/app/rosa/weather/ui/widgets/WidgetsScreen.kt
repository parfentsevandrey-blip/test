package app.rosa.weather.ui.widgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.rosa.weather.R
import app.rosa.weather.core.designsystem.component.GlassButton
import app.rosa.weather.core.designsystem.component.GlassSurface
import app.rosa.weather.core.designsystem.component.RosaIcon
import app.rosa.weather.core.designsystem.component.RosaIconView
import app.rosa.weather.core.designsystem.glass.GlassStyle
import app.rosa.weather.core.designsystem.haptics.LocalHaptics
import app.rosa.weather.core.designsystem.theme.Rosa
import app.rosa.weather.ui.common.GlassScreen
import app.rosa.weather.widget.provider.WidgetKind
import app.rosa.weather.widget.studio.WidgetPreview
import app.rosa.weather.widget.studio.WidgetStudioActivity
import kotlin.math.min

@Composable
fun WidgetsRoute(viewModel: WidgetsViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = LocalHaptics.current
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    val unsupported = stringResource(R.string.widgets_pin_unsupported)

    GlassScreen(stringResource(R.string.widgets_title), onBack) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = bottom + 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.widgets_placed), style = Rosa.type.headline, color = Rosa.colors.ink, modifier = Modifier.semantics { heading() })
            if (state.placed.isEmpty()) {
                Text(stringResource(R.string.widgets_none), style = Rosa.type.body, color = Rosa.colors.inkSoft)
            }
            state.placed.forEach { widget ->
                val size = widget.size
                val (w, h) = if (size != null) {
                    val scale = min(1f, 340f / size.width)
                    size.width * scale to size.height * scale
                } else {
                    320f to 160f
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clickable(remember { MutableInteractionSource() }, null, role = Role.Button) {
                            haptics?.press()
                            context.startActivity(
                                Intent(context, WidgetStudioActivity::class.java)
                                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widget.id),
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    WidgetPreview(widget.config, widget.content, Modifier.size(w.dp, h.dp))
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.widgets_add), style = Rosa.type.headline, color = Rosa.colors.ink, modifier = Modifier.semantics { heading() })
            val sample = state.sampleContent
            WidgetKind.entries.forEach { kind ->
                GlassSurface(Modifier.fillMaxWidth(), style = GlassStyle.Frosted, cornerRadius = 30.dp, contentPadding = PaddingValues(16.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        if (sample != null) {
                            val (w, h) = when (kind) {
                                WidgetKind.Glass -> 300f to 150f
                                WidgetKind.Sky -> 160f to 160f
                                WidgetKind.Almanac -> 230f to 240f
                                WidgetKind.Calendar -> 300f to 270f
                            }
                            WidgetPreview(kind.defaultConfig, sample, Modifier.size(w.dp, h.dp))
                        }
                        Spacer(Modifier.height(12.dp))
                        GlassButton(onClick = {
                            haptics?.confirm()
                            val manager = context.getSystemService(AppWidgetManager::class.java)
                            if (manager?.isRequestPinAppWidgetSupported == true) {
                                manager.requestPinAppWidget(ComponentName(context, kind.providerClass), null, null)
                            } else {
                                Toast.makeText(context, unsupported, Toast.LENGTH_LONG).show()
                            }
                        }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RosaIconView(RosaIcon.Plus, Rosa.colors.ink, size = 16.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.widgets_add), style = Rosa.type.label, color = Rosa.colors.ink)
                            }
                        }
                    }
                }
            }
        }
    }
}
