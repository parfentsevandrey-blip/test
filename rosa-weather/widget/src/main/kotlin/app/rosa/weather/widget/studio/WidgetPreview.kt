package app.rosa.weather.widget.studio

import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.rosa.weather.core.model.CalendarMonth
import app.rosa.weather.core.model.WidgetConfig
import app.rosa.weather.core.model.WidgetFace
import app.rosa.weather.core.model.momentAt
import app.rosa.weather.widget.calendar.CalendarEvents
import app.rosa.weather.widget.motion.LiveWeather
import app.rosa.weather.widget.motion.showLiveWeather
import app.rosa.weather.widget.render.DynamicTones
import app.rosa.weather.widget.render.WidgetContent
import app.rosa.weather.widget.render.WidgetRenderRequest
import app.rosa.weather.widget.render.WidgetRenderer
import app.rosa.weather.widget.render.calendar.CalendarView
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Live, pixel-identical widget preview: the exact renderer the home screen uses, rendered into a
 * bitmap off the main thread, just as the launcher gets it. The preview only draws that bitmap, so
 * a list of previews scrolls for free. While it is being resized ([resizing]) it renders at half
 * resolution and the last picture stretches along until the next is ready, a frame or two later.
 * With [live], rain, snow and lightning move over it on the same tiles the launcher plays.
 */
@Composable
fun WidgetPreview(
    config: WidgetConfig,
    content: WidgetContent,
    modifier: Modifier = Modifier,
    cornerRadiusDp: Float = 24f,
    systemNight: Boolean = false,
    resizing: Boolean = false,
    live: Boolean = false,
) {
    val context = LocalContext.current
    val renderer = remember(context) { WidgetRenderer(context) }
    val dynamic = remember(context) { runCatching { DynamicTones.from(context) }.getOrDefault(DynamicTones.Fallback) }
    val description = remember(content) { renderer.describe(content) }
    val weather = remember(config, content, live) {
        if (config.face == WidgetFace.Calendar) {
            if (live) LiveWeather.ofSeason(config, CalendarView.at(content.nowEpochSeconds).month.monthValue) else null
        } else {
            val moment = content.forecast?.takeIf { live && content.status == WidgetContent.Status.Ready }?.momentAt(content.nowEpochSeconds)
            LiveWeather.of(config, moment)
        }
    }
    val density = LocalDensity.current.density
    // The renderer isn't thread-safe: one render at a time per preview, the latest request wins.
    val worker = remember { Dispatchers.Default.limitedParallelism(1) }
    var pixels by remember { mutableStateOf(IntSize.Zero) }
    var picture by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(pixels, config, content, cornerRadiusDp, systemNight, resizing, weather) {
        val widthDp = pixels.width / density
        val heightDp = pixels.height / density
        if (widthDp < 8f || heightDp < 8f) return@LaunchedEffect
        val request = WidgetRenderRequest(widthDp, heightDp, config, content, cornerRadiusDp, systemNight, dynamic, live = weather != null)
        val scale = if (resizing) density / 2 else density
        picture = withContext(worker) {
            // A calendar shows this month, with the phone's events when they are wanted and allowed.
            val view = if (config.face == WidgetFace.Calendar) {
                val today = CalendarView.at(content.nowEpochSeconds)
                val grid = CalendarMonth.of(today.month, today.today, CalendarMonth.firstDayFor(config.calendar.weekStart, today.locale))
                today.copy(events = if (config.calendar.events) CalendarEvents.read(context, grid.first, grid.last) else emptyMap())
            } else {
                null
            }
            renderer.render(request.copy(calendar = view), scale).asImageBitmap()
        }
    }
    Box(modifier.onSizeChanged { pixels = it }.semantics { contentDescription = description }) {
        Canvas(Modifier.fillMaxSize()) {
            val image = picture ?: return@Canvas
            drawImage(
                image,
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                filterQuality = FilterQuality.Low,
            )
        }
        if (weather != null && pixels.width > 0) {
            val columns = weather.columns(pixels.width / density)
            val rows = weather.rows(pixels.height / density)
            // New tiles only when the grid changes: resizing within it keeps the rain falling.
            key(weather, columns, rows) {
                AndroidView(
                    factory = { ctx ->
                        FrameLayout(ctx).apply {
                            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                            showLiveWeather(weather, pixels.width / density, pixels.height / density)
                        }
                    },
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(cornerRadiusDp.dp)),
                )
            }
        }
    }
}
