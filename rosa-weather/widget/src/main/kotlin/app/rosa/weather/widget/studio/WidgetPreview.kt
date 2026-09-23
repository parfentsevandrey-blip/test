package app.rosa.weather.widget.studio

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.core.graphics.withScale
import app.rosa.weather.core.model.WidgetConfig
import app.rosa.weather.widget.render.DynamicTones
import app.rosa.weather.widget.render.WidgetContent
import app.rosa.weather.widget.render.WidgetRenderRequest
import app.rosa.weather.widget.render.WidgetRenderer

/**
 * Live, pixel-identical widget preview: the exact renderer the home screen uses, drawn straight
 * onto a Compose canvas at whatever size the modifier gives it.
 */
@Composable
fun WidgetPreview(
    config: WidgetConfig,
    content: WidgetContent,
    modifier: Modifier = Modifier,
    cornerRadiusDp: Float = 24f,
    systemNight: Boolean = false,
) {
    val context = LocalContext.current
    val renderer = remember(context) { WidgetRenderer(context) }
    val dynamic = remember(context) { runCatching { DynamicTones.from(context) }.getOrDefault(DynamicTones.Fallback) }
    val description = remember(content) { renderer.describe(content) }
    Canvas(modifier.semantics { contentDescription = description }) {
        val wDp = size.width / density
        val hDp = size.height / density
        if (wDp < 8f || hDp < 8f) return@Canvas
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.withScale(density, density) {
                renderer.draw(this, WidgetRenderRequest(wDp, hDp, config, content, cornerRadiusDp, systemNight, dynamic))
            }
        }
    }
}
