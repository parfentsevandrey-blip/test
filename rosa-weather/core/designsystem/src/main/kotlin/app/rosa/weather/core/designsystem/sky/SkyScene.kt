package app.rosa.weather.core.designsystem.sky

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import android.graphics.BitmapShader
import android.graphics.Canvas as AndroidCanvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateValueAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import app.rosa.weather.core.designsystem.haptics.RosaHaptics
import app.rosa.weather.core.designsystem.motion.LocalAmbientClock
import app.rosa.weather.core.designsystem.theme.toColor
import app.rosa.weather.core.model.ForecastMoment
import app.rosa.weather.core.model.SkyPalette
import app.rosa.weather.core.model.WeatherVisual
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Everything the scene shaders need, as plain values that can be interpolated. */
@Immutable
data class SkyParams(
    val zenith: Color,
    val horizon: Color,
    val glow: Color,
    val sun: Color,
    val cloudLight: Color,
    val cloudShade: Color,
    /** Where the sun/moon is on its daily path: 0 rising in the east … 1 setting in the west. */
    val bodyPath: Float,
    /** Its height: 0 on the horizon … 1 high in the sky (negative while it sinks below). */
    val bodyLift: Float,
    val isSun: Boolean,
    /** 0 when the sun/moon is below the horizon, 1 when clearly above it. */
    val bodyVisible: Float,
    val moonPhase: Float,
    val cloudCover: Float,
    val cloudDark: Float,
    val fog: Float,
    val wind: Float,
    val stars: Float,
    val rain: Float,
    val snow: Float,
    val lightning: Float,
    val frost: Float,
    val condensation: Float,
) {
    fun lerp(to: SkyParams, t: Float): SkyParams {
        fun f(a: Float, b: Float) = a + (b - a) * t
        return SkyParams(
            lerp(zenith, to.zenith, t), lerp(horizon, to.horizon, t), lerp(glow, to.glow, t), lerp(sun, to.sun, t),
            lerp(cloudLight, to.cloudLight, t), lerp(cloudShade, to.cloudShade, t),
            f(bodyPath, to.bodyPath), f(bodyLift, to.bodyLift), if (t < 0.5f) isSun else to.isSun, f(bodyVisible, to.bodyVisible), f(moonPhase, to.moonPhase),
            f(cloudCover, to.cloudCover), f(cloudDark, to.cloudDark), f(fog, to.fog), f(wind, to.wind), f(stars, to.stars),
            f(rain, to.rain), f(snow, to.snow), f(lightning, to.lightning), f(frost, to.frost), f(condensation, to.condensation),
        )
    }

    companion object {
        /**
         * Builds the scene for a moment: the sun (or moon) where it really is on its daily path,
         * the palette for that elevation and weather, window effects from temperature and
         * humidity. Where that path is drawn on screen is up to the [SkyStage].
         */
        fun from(moment: ForecastMoment, palette: SkyPalette): SkyParams {
            val visual: WeatherVisual = moment.visual
            val useSun = moment.sun.elevation > -5
            val body = if (useSun) moment.sun else moment.moon
            // The east–west component of the azimuth: rising bodies are east, setting ones west,
            // at any latitude — and it never jumps when the azimuth wraps through north.
            val path = ((1.0 - sin(Math.toRadians(body.azimuth))) / 2.0).toFloat()
            val lift = (body.elevation / 50.0).toFloat().coerceIn(-0.25f, 1f)
            val night = ((-moment.sun.elevation - 6) / 8.0).toFloat().coerceIn(0f, 1f)
            val humid = ((moment.humidity - 88) / 12f).coerceIn(0f, 1f)
            return SkyParams(
                zenith = palette.zenith.toColor(), horizon = palette.horizon.toColor(), glow = palette.glow.toColor(),
                sun = palette.sun.toColor(), cloudLight = palette.cloudLight.toColor(), cloudShade = palette.cloudShade.toColor(),
                bodyPath = path, bodyLift = lift, isSun = useSun,
                bodyVisible = ((body.elevation + 2.0) / 5.0).toFloat().coerceIn(0f, 1f),
                moonPhase = moment.moonPhase.phase.toFloat(),
                cloudCover = visual.cloudCover, cloudDark = visual.cloudDarkness, fog = visual.fog, wind = visual.wind,
                stars = night * (1f - visual.cloudCover * 0.85f), rain = visual.rain, snow = visual.snow,
                lightning = visual.lightning,
                frost = ((0.5 - moment.temperature) / 8.0).toFloat().coerceIn(0f, 0.9f),
                condensation = maxOf(visual.fog * 0.8f, humid * 0.6f, if (visual.rain > 0.2f) 0.25f else 0f),
            )
        }
    }
}

/** How much GPU the scene may spend: the render scale of the offscreen sky. */
enum class SceneQuality(val scale: Float, val windowEffects: Boolean) {
    Battery(0.33f, false),
    Balanced(0.5f, true),
    Cinematic(0.75f, true),
}

/**
 * The living backdrop of the app: animated sky, precipitation and the "window pane" with rain
 * beads, frost and condensation you can wipe with a finger. Taps send ripples across the glass.
 * Transitions between weather states are interpolated, never cut.
 */
@Composable
fun SkyScene(
    params: SkyParams,
    modifier: Modifier = Modifier,
    stage: SkyStage = SkyStage.Default,
    quality: SceneQuality = SceneQuality.Balanced,
    tilt: State<Offset>? = null,
    animate: Boolean = true,
    haptics: RosaHaptics? = null,
    interactive: Boolean = true,
    transitionMillis: Int = 1400,
) {
    val sky = remember { RuntimeShader(SKY_SHADER) }
    val precip = remember { RuntimeShader(PRECIPITATION_SHADER) }
    val window = remember { RuntimeShader(WINDOW_SHADER) }
    val skyBrush = remember { ShaderBrush(sky) }
    val precipBrush = remember { ShaderBrush(precip) }
    val layer = rememberGraphicsLayer()
    // Ambient motion runs on the shared, unhurried clock; only a tap ripple, a lightning flash and
    // weather transitions animate at the display's full rate, and only while they last.
    val clock = LocalAmbientClock.current
    val flash = remember { Animatable(0f) }
    val ripple = remember { RippleState() }
    val rippleAge = remember { Animatable(RIPPLE_SECONDS) }
    val scope = rememberCoroutineScope()
    val wipe = remember { WipeMask() }
    val currentHaptics by rememberUpdatedState(haptics)
    // A newly measured stage (another city's wider numerals, rotation) glides, never jumps.
    val stageState = animateValueAsState(stage, SkyStage.VectorConverter, spring(stiffness = Spring.StiffnessVeryLow), label = "stage")

    // Interpolate between weather/time states instead of cutting.
    val from = remember { mutableParams(params) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(params, transitionMillis) {
        if (from.target != params) {
            from.start = from.current(progress.value)
            from.target = params
            if (transitionMillis <= 0) {
                progress.snapTo(1f)
                from.start = params
            } else {
                progress.snapTo(0f)
                progress.animateTo(1f, tween(transitionMillis))
            }
        }
    }

    // Lightning: irregular flashes, thunder felt a moment later (light travels faster than sound).
    LaunchedEffect(params.lightning > 0.1f, animate) {
        if (params.lightning <= 0.1f || !animate) return@LaunchedEffect
        while (isActive) {
            delay(Random.nextLong(3_500, 11_000))
            val distance = Random.nextFloat()
            launch {
                delay((250 + distance * 2200).toLong())
                currentHaptics?.thunder(distance)
            }
            flash.snapTo(0.9f - distance * 0.4f)
            flash.animateTo(0.15f, tween(70))
            flash.animateTo(0.7f - distance * 0.3f, tween(50))
            flash.animateTo(0f, tween(420))
        }
    }

    // Condensation slowly returns where it was wiped.
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(200)
            wipe.fade()
        }
    }

    val input = if (interactive) {
        Modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                ripple.position = down.position
                scope.launch {
                    rippleAge.snapTo(0f)
                    rippleAge.animateTo(RIPPLE_SECONDS, tween((RIPPLE_SECONDS * 1000).toInt(), easing = LinearEasing))
                }
                currentHaptics?.raindrop()
                var last = down.position
                wipe.stroke(last, last, size)
                do {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull() ?: break
                    if (change.pressed) {
                        wipe.stroke(last, change.position, size)
                        if ((change.position - last).getDistance() > 48f) currentHaptics?.raindrop()
                        last = change.position
                    }
                } while (event.changes.any { it.pressed })
            }
        }
    } else {
        Modifier
    }

    Canvas(modifier.then(input)) {
        val p = from.current(progress.value)
        val s = quality.scale
        val w = max(1, (size.width * s).roundToInt())
        val h = max(1, (size.height * s).roundToInt())
        val t = clock.seconds
        // Sampled, not observed: tilt shows up on the next tick instead of forcing extra redraws.
        val tiltValue = tilt?.let { Snapshot.withoutReadObservation { it.value } } ?: Offset.Zero

        sky.setFloatUniform("resolution", w.toFloat(), h.toFloat())
        sky.setFloatUniform("time", t)
        sky.setColorUniform("zenith", p.zenith.toArgb())
        sky.setColorUniform("horizon", p.horizon.toArgb())
        sky.setColorUniform("glow", p.glow.toArgb())
        sky.setColorUniform("sunColor", p.sun.toArgb())
        sky.setColorUniform("cloudLight", p.cloudLight.toArgb())
        sky.setColorUniform("cloudShade", p.cloudShade.toArgb())
        val body = stageState.value.at(p.bodyPath, p.bodyLift)
        sky.setFloatUniform("sunPos", body.x, body.y)
        sky.setFloatUniform("isSun", if (p.isSun) 1f else 0f)
        sky.setFloatUniform("bodySize", (if (p.isSun) SUN_RADIUS else SkyStage.BODY_RADIUS) * p.bodyVisible)
        sky.setFloatUniform("moonPhase", p.moonPhase)
        sky.setFloatUniform("cloudCover", p.cloudCover)
        sky.setFloatUniform("cloudDark", p.cloudDark)
        sky.setFloatUniform("fog", p.fog)
        sky.setFloatUniform("wind", p.wind)
        sky.setFloatUniform("stars", p.stars)
        sky.setFloatUniform("flash", flash.value)
        sky.setFloatUniform("tilt", tiltValue.x, tiltValue.y)

        val precipitating = p.rain > 0.02f || p.snow > 0.02f
        if (precipitating) {
            precip.setFloatUniform("resolution", w.toFloat(), h.toFloat())
            precip.setFloatUniform("time", t)
            precip.setFloatUniform("rain", p.rain)
            precip.setFloatUniform("snow", p.snow)
            precip.setFloatUniform("wind", p.wind)
            precip.setFloatUniform("tilt", tiltValue.x, tiltValue.y)
        }

        val age = rippleAge.value
        val rippleActive = age < RIPPLE_SECONDS
        val windowOn = quality.windowEffects &&
            (p.rain > 0.05f || p.frost > 0.02f || p.condensation > 0.05f || rippleActive)
        if (windowOn) {
            window.setFloatUniform("resolution", w.toFloat(), h.toFloat())
            window.setFloatUniform("time", t)
            window.setFloatUniform("drops", (p.rain * 1.1f).coerceAtMost(1f))
            window.setFloatUniform("frost", p.frost)
            window.setFloatUniform("fogged", p.condensation)
            window.setFloatUniform(
                "ripple",
                ripple.position.x * s, ripple.position.y * s, t - age, if (rippleActive) ripple.strength else 0f,
            )
            window.setInputShader("wipe", wipe.shader(w, h))
            layer.renderEffect = RenderEffect.createRuntimeShaderEffect(window, "content").asComposeRenderEffect()
        } else {
            layer.renderEffect = null
        }
        layer.compositingStrategy = CompositingStrategy.Offscreen
        layer.record(IntSize(w, h)) {
            drawRect(skyBrush)
            if (precipitating) drawRect(precipBrush)
        }
        scale(1f / s, 1f / s, pivot = Offset.Zero) { drawLayer(layer) }
    }
}

private const val SUN_RADIUS = 0.022f

private class MutableParams(start: SkyParams, target: SkyParams) {
    var start by mutableStateOf(start)
    var target by mutableStateOf(target)

    fun current(t: Float): SkyParams = if (t >= 1f) target else start.lerp(target, t)
}

private fun mutableParams(p: SkyParams) = MutableParams(p, p)

private const val RIPPLE_SECONDS = 2.5f

private class RippleState {
    var position = Offset.Zero
    var strength = 1f
}

/**
 * Low-resolution alpha mask of where a finger has wiped the fogged pane. Painted on the CPU
 * (a few hundred pixels), sampled by the window shader, and slowly faded so the mist returns.
 */
private class WipeMask {
    private val bitmap: Bitmap = createBitmap(90, 180)
    private val canvas = AndroidCanvas(bitmap)
    private val brush = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 11f
        maskFilter = android.graphics.BlurMaskFilter(4f, android.graphics.BlurMaskFilter.Blur.NORMAL)
    }
    private val fader = Paint().apply {
        color = android.graphics.Color.argb(5, 0, 0, 0)
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    }
    private val bitmapShader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    private val matrix = Matrix()
    private val empty = LinearGradient(0f, 0f, 1f, 1f, 0, 0, Shader.TileMode.CLAMP)
    private var dirty = false
    private var fades = 0
    private val version = mutableLongStateOf(0L)

    fun stroke(from: Offset, to: Offset, size: IntSize) {
        if (size.width == 0 || size.height == 0) return
        val sx = bitmap.width / size.width.toFloat()
        val sy = bitmap.height / size.height.toFloat()
        canvas.drawLine(from.x * sx, from.y * sy, to.x * sx, to.y * sy, brush)
        dirty = true
        fades = 0
        version.longValue++
    }

    fun fade() {
        if (!dirty) return
        canvas.drawRect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(), fader)
        // 5/255 per step: after ~52 steps the mist is fully back; stop redrawing for it.
        if (++fades > 55) {
            bitmap.eraseColor(android.graphics.Color.TRANSPARENT)
            dirty = false
        }
        version.longValue++
    }

    fun shader(w: Int, h: Int): Shader {
        if (!dirty) return empty
        version.longValue // observed so redraws follow wipes
        matrix.setScale(w / bitmap.width.toFloat(), h / bitmap.height.toFloat())
        bitmapShader.setLocalMatrix(matrix)
        return bitmapShader
    }
}
