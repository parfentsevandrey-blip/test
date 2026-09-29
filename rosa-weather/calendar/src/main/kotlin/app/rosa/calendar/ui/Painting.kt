package app.rosa.calendar.ui

import android.view.View
import android.widget.FrameLayout
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import app.rosa.weather.core.designsystem.component.LocalBackdrop
import app.rosa.weather.core.designsystem.glass.LocalGlassEnvironment
import app.rosa.weather.core.designsystem.glass.backdropSource
import app.rosa.weather.core.designsystem.glass.rememberBackdrop
import app.rosa.weather.core.designsystem.motion.LocalMotionEnabled
import app.rosa.weather.core.designsystem.theme.toColor
import app.rosa.weather.core.model.Argb
import app.rosa.weather.core.model.SkyPalette
import app.rosa.weather.widget.motion.LiveWeather
import app.rosa.weather.widget.motion.showLiveWeather
import app.rosa.weather.widget.render.calendar.CalendarArt
import app.rosa.weather.widget.render.calendar.WeekArt
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The colours of the whole app for a week's painting: its sky, its light, its accent. */
object PaintingPalette {
    private val Cream = Argb.hex(0xFFFBF5)
    private val Deep = Argb.hex(0x1B2030)

    fun of(art: WeekArt, night: Boolean): SkyPalette {
        val dark = night || art.dark
        val ink = if (dark) Cream else Deep
        // By moonlight the painting's own sky sinks toward the night.
        val zenith = if (night) art.zenith.lerp(Argb.hex(0x0A1024), 0.62f) else art.zenith
        val horizon = if (night) art.horizon.lerp(Argb.hex(0x151B38), 0.66f) else art.horizon
        return SkyPalette(
            zenith = zenith,
            horizon = horizon,
            glow = art.bodyColor,
            sun = art.bodyColor,
            cloudLight = Argb.White,
            cloudShade = Argb.hex(0x8A93A6),
            ink = ink,
            inkSoft = ink.withAlpha(0.72f),
            accent = legible(art.accent, dark),
            warm = art.accent,
            cool = zenith,
            // A painting is never as even as a sky: its lamps, snow and cups are bright under light type.
            // The glass over a dark one is made as dense as over the brightest sky, so type reads anywhere.
            brightness = when {
                night -> 0.3
                dark -> 0.38
                else -> 0.62
            },
        )
    }

    /**
     * The painting's accent, deepened on milky glass and lightened on smoky glass until it
     * stands out from the pane like the ink does.
     */
    fun legible(accent: Argb, darkGlass: Boolean): Argb {
        var c = accent
        repeat(8) {
            if (darkGlass && c.luminance < 0.34) c = c.lerp(Argb.White, 0.18f)
            if (!darkGlass && c.luminance > 0.22) c = c.lerp(Argb.Black, 0.16f)
        }
        return c
    }
}

/**
 * The week's painting behind the whole app, as the glass on top refracts it: painted once for the
 * screen (then kept, see [CalendarArt]) and eased in over what was there. Its sun or moon lights
 * the glass, winter frosts its edges, it sways a little as the phone tilts, and the week's own
 * motion — snow, leaves, fireflies, falling stars — plays over it when [live].
 */
@Composable
fun PaintingBackdrop(
    art: WeekArt,
    night: Boolean,
    live: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val density = LocalDensity.current
    val backdrop = rememberBackdrop()
    val environment = LocalGlassEnvironment.current
    val motion = LocalMotionEnabled.current
    val weather: LiveWeather? = if (live && motion) art.motion else null
    BoxWithConstraints(modifier.fillMaxSize()) {
        val widthDp = maxWidth.value
        val heightDp = maxHeight.value
        val bleed = if (motion) PARALLAX_BLEED else 0f
        val pixels = paintingPixels(widthDp, heightDp, density.density, sways = motion)
        val fade = remember { Animatable(1f) }
        var shown by remember { mutableStateOf<Painted?>(null) }
        var previous by remember { mutableStateOf<Painted?>(null) }
        LaunchedEffect(art.week, night, weather != null, pixels) {
            if (pixels.width < 1 || pixels.height < 1) return@LaunchedEffect
            val painted = withContext(Dispatchers.Default) {
                val bitmap = CalendarArt.painting(context, art, pixels.width, pixels.height, pixels.pxPerDp, live = weather != null, night = night)
                Painted(art.week, night, bitmap.asImageBitmap())
            }
            if (shown == null || !motion) {
                shown = painted
                previous = null
                fade.snapTo(1f)
            } else if (shown?.week != painted.week || shown?.night != painted.night || shown?.image?.width != painted.image.width) {
                previous = shown
                shown = painted
                fade.snapTo(0f)
                fade.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
                previous = null
            } else {
                shown = painted
            }
        }
        // The glass is lit from where the painting's sun or moon is, in its colour; winter frosts it.
        val lightX = widthDp * art.bodyX
        val lightY = heightDp * art.bodyY
        var origin by remember { mutableStateOf(Offset.Zero) }
        LaunchedEffect(art.week, night, origin, widthDp, heightDp) {
            val px = density.density
            environment.publishScene(
                position = Offset(origin.x + lightX * px, origin.y + lightY * px),
                color = Argb.White.lerp(art.bodyColor, 0.65f).toColor(),
                power = art.bodyPower * if (night && !art.isMoon) 0.35f else 1f,
                sky = PaintingPalette.of(art, night).zenith.toColor(),
                flash = 0f,
                frost = (art.season.frost * 0.7f).coerceIn(0f, 1f),
                // The week's weather settles on the glass too: beads in its rain, a cap of its snow.
                rain = when (art.motion) {
                    LiveWeather.RainLight, LiveWeather.Drips -> 0.4f
                    LiveWeather.RainHeavy, LiveWeather.Storm -> 0.8f
                    else -> 0f
                },
                snow = when (art.motion) {
                    LiveWeather.SnowLight -> 0.45f
                    LiveWeather.SnowHeavy, LiveWeather.Blizzard -> 0.85f
                    else -> 0f
                },
            )
        }
        val placeholder = remember(art.week, night) { art.sky.map { (if (night) it.lerp(Argb.hex(0x0A1024), 0.6f) else it).toColor() } }
        val scrim = PaintingPalette.of(art, night).zenith.toColor()
        Canvas(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { origin = it.positionInRoot() }
                .backdropSource(backdrop),
        ) {
            // Until the painting is ready: its sky, as a gradient.
            if (shown == null) drawRect(Brush.verticalGradient(placeholder))
            val tilt = if (motion) environment.tilt else Offset.Zero
            val sway = Offset(-tilt.x * size.width * bleed, -tilt.y * size.height * bleed)
            previous?.let { drawPainting(it.image, sway, bleed, 1f) }
            shown?.let { drawPainting(it.image, sway, bleed, if (previous != null) fade.value else 1f) }
            // The sky at the top deepened a little, so the month's name and the clock read on any painting.
            drawRect(
                Brush.verticalGradient(
                    0f to scrim.copy(alpha = 0.42f),
                    0.22f to scrim.copy(alpha = 0.12f),
                    0.4f to Color.Transparent,
                ),
            )
        }
        if (weather != null) {
            val columns = weather.columns(widthDp)
            val rows = weather.rows(heightDp)
            // New tiles only when the motion or its grid changes: the snow keeps falling otherwise.
            key(weather, columns, rows) {
                AndroidView(
                    factory = { ctx ->
                        FrameLayout(ctx).apply {
                            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                            showLiveWeather(weather, widthDp, heightDp)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        CompositionLocalProvider(LocalBackdrop provides backdrop) {
            content()
        }
    }
}

private class Painted(val week: Int, val night: Boolean, val image: ImageBitmap)

/** The pixels of the painting behind a screen of [widthDp] × [heightDp]. */
internal data class PaintingPixels(val width: Int, val height: Int, val pxPerDp: Float)

/**
 * A screen's painting: a little larger than the screen when it [sways] with the tilt, so no edge
 * ever shows, and at two pixels a dp at most — a painting is soft, and so it stays in the cache.
 */
internal fun paintingPixels(widthDp: Float, heightDp: Float, density: Float, sways: Boolean): PaintingPixels {
    val grow = 1f + (if (sways) PARALLAX_BLEED else 0f) * 2f
    val pxPerDp = min(density, 2f)
    return PaintingPixels((widthDp * grow * pxPerDp).roundToInt(), (heightDp * grow * pxPerDp).roundToInt(), pxPerDp)
}

/** How much bigger than the screen the painting is on each side, for the tilt to sway it. */
private const val PARALLAX_BLEED = 0.025f

private fun DrawScope.drawPainting(image: ImageBitmap, sway: Offset, bleed: Float, alpha: Float) {
    // The painting covers the screen and its bleed; the tilt moves it within the bleed.
    val w = size.width * (1f + bleed * 2f)
    val h = size.height * (1f + bleed * 2f)
    withTransform({ translate(-size.width * bleed + sway.x, -size.height * bleed + sway.y) }) {
        drawImage(
            image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(image.width, image.height),
            dstSize = IntSize(w.roundToInt(), h.roundToInt()),
            alpha = alpha,
            filterQuality = FilterQuality.Medium,
        )
    }
}
