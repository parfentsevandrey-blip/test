package app.papersky.weather.widget.config

import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.glance.appwidget.compose
import app.papersky.weather.widget.PaperskyWidget
import app.papersky.weather.widget.WidgetConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * The real thing, not a mock: runs the widget's Glance composition for [config] at [size] and
 * inflates the resulting RemoteViews, exactly as a launcher would.
 */
@Composable
fun WidgetPreview(config: WidgetConfig, size: DpSize, modifier: Modifier = Modifier, debounceMillis: Long = 60) {
    val context = LocalContext.current
    var views by remember { mutableStateOf<RemoteViews?>(null) }
    LaunchedEffect(config, size) {
        delay(debounceMillis)
        views = withContext(Dispatchers.Default) {
            try {
                PaperskyWidget().compose(context.applicationContext, size = size, state = config.toPreferences())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        } ?: views
    }
    Box(modifier.size(size)) {
        AndroidView(
            factory = { FrameLayout(it) },
            modifier = Modifier.fillMaxSize(),
            update = { frame ->
                val rv = views ?: return@AndroidView
                frame.removeAllViews()
                runCatching { rv.apply(frame.context, frame) }.getOrNull()?.let {
                    frame.addView(it, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                }
            },
        )
        // Swallow touches: preview widgets must not launch anything.
        Box(
            Modifier.fillMaxSize().pointerInput(Unit) {
                awaitEachGesture { awaitFirstDown(requireUnconsumed = false).consume() }
            },
        )
    }
}
