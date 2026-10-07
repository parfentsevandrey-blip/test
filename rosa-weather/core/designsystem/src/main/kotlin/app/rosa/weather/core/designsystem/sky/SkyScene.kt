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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntSize
import app.rosa.weather.core.designsystem.glass.GlassEnvironment
import app.rosa.weather.core.designsystem.haptics.RosaHaptics
import app.rosa.weather.core.designsystem.motion.LocalAmbientClock
import app.rosa.weather.core.designsystem.theme.toColor
import app.rosa.weather.core.model.Appearance
import app.rosa.weather.core.model.ForecastMoment
import app.rosa.weather.core.model.SkyPalette
import app.rosa.weather.core.model.WeatherCondition
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
    /** A rainbow opposite the sun after a shower (0..1). */
    val rainbow: Float = 0f,
    /** 0..1: clear sunshine: motes glinting in the air, the sun flaring in the lens. */
    val sunlitAir: Float = 0f,
    /** 0..1: a frosty clear day's diamond dust, ice crystals flashing in the air. */
    val diamondDust: Float = 0f,
    /**
     * 0..1: the cozy mood — the town's lights below the sky, a lamp in the room lighting the glass,
     * the window misted from inside along its foot.
     */
    val cozy: Float = 0f,
) {
    fun lerp(to: SkyParams, t: Float): SkyParams {
        fun f(a: Float, b: Float) = a + (b - a) * t
        return SkyParams(
            lerp(zenith, to.zenith, t), lerp(horizon, to.horizon, t), lerp(glow, to.glow, t), lerp(sun, to.sun, t),
            lerp(cloudLight, to.cloudLight, t), lerp(cloudShade, to.cloudShade, t),
            f(bodyPath, to.bodyPath), f(bodyLift, to.bodyLift), if (t < 0.5f) isSun else to.isSun, f(bodyVisible, to.bodyVisible), f(moonPhase, to.moonPhase),
            f(cloudCover, to.cloudCover), f(cloudDark, to.cloudDark), f(fog, to.fog), f(wind, to.wind), f(stars, to.stars),
            f(rain, to.rain), f(snow, to.snow), f(lightning, to.lightning), f(frost, to.frost), f(condensation, to.condensation),
            f(rainbow, to.rainbow), f(sunlitAir, to.sunlitAir), f(diamondDust, to.diamondDust), f(cozy, to.cozy),
        )
    }

    companion object {
        /**
         * Builds the scene for a moment: the sun (or moon) where it really is on its daily path,
         * the palette for that elevation and weather, window effects from temperature and
         * humidity. Where that path is drawn on screen is up to the [SkyStage].
         */
        fun from(moment: ForecastMoment, palette: SkyPalette, appearance: Appearance = Appearance.Auto): SkyParams {
            val visual: WeatherVisual = moment.visual
            // Fixed moods keep real weather but no real sun or moon: a noon sun in "Dark" (or the
            // moon over a porcelain sky) would contradict the light they set.
            val realSky = appearance == Appearance.Auto
            val useSun = moment.sun.elevation > -5
            val body = if (useSun) moment.sun else moment.moon
            // The east–west component of the azimuth: rising bodies are east, setting ones west,
            // at any latitude — and it never jumps when the azimuth wraps through north.
            val path = ((1.0 - sin(Math.toRadians(body.azimuth))) / 2.0).toFloat()
            val lift = (body.elevation / 50.0).toFloat().coerceIn(-0.25f, 1f)
            val night = when (appearance) {
                Appearance.Auto -> ((-moment.sun.elevation - 6) / 8.0).toFloat().coerceIn(0f, 1f)
                Appearance.Dark -> 1f
                Appearance.Evening -> 0.3f // the first stars of the blue hour
                Appearance.Cozy -> 0.4f
                Appearance.Light -> 0f
            }
            return SkyParams(
                zenith = palette.zenith.toColor(), horizon = palette.horizon.toColor(), glow = palette.glow.toColor(),
                sun = palette.sun.toColor(), cloudLight = palette.cloudLight.toColor(), cloudShade = palette.cloudShade.toColor(),
                bodyPath = path, bodyLift = lift, isSun = useSun,
                bodyVisible = if (realSky) ((body.elevation + 2.0) / 5.0).toFloat().coerceIn(0f, 1f) else 0f,
                moonPhase = moment.moonPhase.phase.toFloat(),
                cloudCover = visual.cloudCover, cloudDark = visual.cloudDarkness, fog = visual.fog, wind = visual.wind,
                stars = night * (1f - visual.cloudCover * 0.85f), rain = visual.rain, snow = visual.snow,
                lightning = visual.lightning,
                frost = moment.paneFrost,
                condensation = moment.paneMist,
                rainbow = if (realSky) rainbowFor(moment) else 0f,
                sunlitAir = if (realSky && useSun) clearAir(visual) * ((moment.sun.elevation - 1.0) / 6.0).toFloat().coerceIn(0f, 1f) else 0f,
                // Diamond dust needs a hard frost: it begins about −6°, and is thick by −14°.
                diamondDust = clearAir(visual) * ((-6.0 - moment.temperature) / 8.0).toFloat().coerceIn(0f, 1f),
                cozy = if (appearance == Appearance.Cozy) 1f else 0f,
            )
        }

        /** How clear and dry the air is: no precipitation, little cloud or fog. */
        private fun clearAir(visual: WeatherVisual): Float {
            if (visual.rain + visual.snow > 0.02f) return 0f
            return ((0.7f - visual.cloudCover) / 0.4f).coerceIn(0f, 1f) * (1f - visual.fog).coerceIn(0f, 1f)
        }

        /**
         * A rainbow needs rain falling opposite a sun that shines through broken cloud, and stands
         * about 42° from the point opposite it: only while the sun is lower than that.
         */
        private fun rainbowFor(moment: ForecastMoment): Float {
            val visual = moment.visual
            val elevation = moment.sun.elevation
            val showery = moment.condition == WeatherCondition.RainShowers || (visual.rain in 0.02f..0.6f && visual.cloudCover <= 0.8f)
            if (!showery || visual.lightning > 0.1f) return 0f
            val sunLow = ((40.0 - elevation) / 8.0).coerceIn(0.0, 1.0) * ((elevation - 2.0) / 4.0).coerceIn(0.0, 1.0)
            val broken = ((0.92f - visual.cloudCover) / 0.2f).coerceIn(0f, 1f)
            return (sunLow.toFloat() * broken).coerceIn(0f, 1f)
        }
    }
}

/**
 * How much GPU the scene may spend: the render scale of the offscreen sky, and of the window pane
 * (raindrops, frost, mist) over it — finer, since beads and ice need crisp edges the soft sky
 * doesn't.
 */
enum class SceneQuality(val scale: Float, val windowEffects: Boolean, val windowScale: Float) {
    Battery(0.33f, false, 0.33f),
    Balanced(0.5f, true, 0.75f),
    Cinematic(0.75f, true, 1f),
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
    /** Glass that the scene lights: it learns where the sun or moon is, its colour, the flashes. */
    light: GlassEnvironment? = null,
) {
    val sky = remember { RuntimeShader(SKY_SHADER) }
    val origin = remember { RootOrigin() }
    val precip = remember { RuntimeShader(PRECIPITATION_SHADER) }
    val window = remember { RuntimeShader(WINDOW_SHADER) }
    val skyBrush = remember { ShaderBrush(sky) }
    val precipBrush = remember { ShaderBrush(precip) }
    val layer = rememberGraphicsLayer()
    val pane = rememberGraphicsLayer()
    val baked = rememberGraphicsLayer()
    // Ambient motion runs on the shared, unhurried clock; only a tap ripple, a lightning flash and
    // weather transitions animate at the display's full rate, and only while they last.
    val clock = LocalAmbientClock.current
    val flash = remember { Animatable(0f) }
    val bolt = remember { BoltState() }
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
            // A near strike shows its channel; a distant one only lights the clouds.
            bolt.visible = distance < 0.55f
            bolt.seed = Random.nextFloat() * 100f
            bolt.x = 0.15f + Random.nextFloat() * 0.7f
            bolt.channel = if (bolt.visible) LightningChannel(Random.nextInt(), bolt.x, 0.5f + Random.nextFloat() * 0.32f) else null
            // A near strike lights the clouds above it; a far one somewhere in the deck.
            bolt.flashAt = if (bolt.visible) Offset(bolt.x, 0.12f) else Offset(0.15f + Random.nextFloat() * 0.7f, 0.08f + Random.nextFloat() * 0.3f)
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

    Canvas(modifier.then(input).onGloballyPositioned { origin.value = it.positionInRoot() }) {
        val p = from.current(progress.value)
        val s = quality.scale
        val w = max(1, (size.width * s).roundToInt())
        val h = max(1, (size.height * s).roundToInt())
        val t = clock.seconds
        // Sampled, not observed: tilt shows up on the next tick instead of forcing extra redraws.
        val tiltValue = tilt?.let { Snapshot.withoutReadObservation { it.value } } ?: Offset.Zero

        val body = stageState.value.at(p.bodyPath, p.bodyLift)
        sky.setSkyUniforms(p, body, w, h, t, flash.value, bolt, tiltValue)
        light?.let { env ->
            // The sun lights the glass by its colour, the moon softly, by its phase. Scattered cloud
            // lets their light through; an overcast sky leaves only its own soft light.
            val moonlight = (1f - kotlin.math.cos(2f * kotlin.math.PI.toFloat() * p.moonPhase)) / 2f
            val overcast = ((p.cloudCover - 0.3f) / 0.65f).coerceIn(0f, 1f)
            val clear = 1f - overcast * overcast * (3f - 2f * overcast)
            val power = if (p.isSun) {
                p.bodyVisible * clear
            } else {
                p.bodyVisible * 0.55f * clear * (0.35f + 0.65f * moonlight)
            }
            // The cozy mood lights the glass from inside the room instead: a warm lamp, low on the left.
            val lamp = p.cozy
            val bodyAt = origin.value + Offset(body.x * size.width, body.y * size.height)
            env.publishScene(
                position = if (lamp > 0.5f) origin.value + Offset(-0.3f * size.width, 0.78f * size.height) else bodyAt,
                color = (if (p.isSun) p.sun else MOONLIGHT).let { if (lamp > 0f) lerp(it, LAMPLIGHT, lamp) else it },
                power = max(power, LAMP_POWER * lamp).coerceIn(0f, 1f),
                sky = p.zenith,
                flash = flash.value,
                frost = p.frost,
                rain = p.rain,
                snow = p.snow,
                stars = p.stars,
                clouds = p.cloudCover,
                wind = p.wind,
                cozy = lamp,
            )
        }

        val precipitating = p.rain > 0.02f || p.snow > 0.02f
        val age = rippleAge.value
        val rippleActive = age < RIPPLE_SECONDS
        val windowOn = quality.windowEffects &&
            (p.rain > 0.05f || p.frost > 0.02f || p.condensation > 0.05f || p.cozy > 0.02f || rippleActive)
        // The pane (and the rain, whose streaks it refracts) is drawn finer than the soft sky.
        val ws = if (windowOn) max(quality.windowScale, s) else s
        val pw = max(1, (size.width * ws).roundToInt())
        val ph = max(1, (size.height * ws).roundToInt())

        if (precipitating) precip.setPrecipitationUniforms(p, pw, ph, t, flash.value, tiltValue)

        layer.renderEffect = null
        layer.compositingStrategy = CompositingStrategy.Offscreen
        layer.record(IntSize(w, h)) {
            drawRect(skyBrush)
            if (precipitating && !windowOn) drawRect(precipBrush)
        }
        if (windowOn) {
            window.setFloatUniform("resolution", pw.toFloat(), ph.toFloat())
            window.setFloatUniform("time", t)
            window.setFloatUniform("drops", (p.rain * 1.1f).coerceAtMost(1f))
            window.setFloatUniform("frost", p.frost)
            window.setFloatUniform("fogged", p.condensation)
            window.setFloatUniform("mist", p.cozy)
            window.setFloatUniform(
                "ripple",
                ripple.position.x * ws, ripple.position.y * ws, t - age, if (rippleActive) ripple.strength else 0f,
            )
            window.setInputShader("wipe", wipe.shader(pw, ph))
            pane.renderEffect = RenderEffect.createRuntimeShaderEffect(window, "content").asComposeRenderEffect()
            pane.record(IntSize(pw, ph)) {
                scale(ws / s, ws / s, pivot = Offset.Zero) { drawLayer(layer) }
                if (precipitating) drawRect(precipBrush)
            }
            // The pane effect is baked once per sky frame into a layer of its own. Left on its
            // node it would be re-applied wherever that node is drawn: under every glass element,
            // for its own region, on every frame of a scroll.
            baked.compositingStrategy = CompositingStrategy.Offscreen
            baked.record(IntSize(pw, ph)) { drawLayer(pane) }
            scale(1f / ws, 1f / ws, pivot = Offset.Zero) { drawLayer(baked) }
        } else {
            scale(1f / s, 1f / s, pivot = Offset.Zero) { drawLayer(layer) }
        }
        // A near strike's channel, crisp: drawn at the display's resolution over the soft sky.
        bolt.channel?.let { channel -> if (bolt.visible) drawLightning(channel, flash.value) }
    }
}

/** The sky shader's uniforms for [p] with the sun or moon at [body], at [w] × [h] pixels. */
internal fun RuntimeShader.setSkyUniforms(p: SkyParams, body: Offset, w: Int, h: Int, time: Float, flash: Float, bolt: BoltState, tilt: Offset) {
    setFloatUniform("resolution", w.toFloat(), h.toFloat())
    setFloatUniform("time", time)
    setColorUniform("zenith", p.zenith.toArgb())
    setColorUniform("horizon", p.horizon.toArgb())
    setColorUniform("glow", p.glow.toArgb())
    setColorUniform("sunColor", p.sun.toArgb())
    setColorUniform("cloudLight", p.cloudLight.toArgb())
    setColorUniform("cloudShade", p.cloudShade.toArgb())
    setFloatUniform("sunPos", body.x, body.y)
    setFloatUniform("isSun", if (p.isSun) 1f else 0f)
    setFloatUniform("bodySize", (if (p.isSun) SUN_RADIUS else SkyStage.BODY_RADIUS) * p.bodyVisible)
    setFloatUniform("moonPhase", p.moonPhase)
    setFloatUniform("cloudCover", p.cloudCover)
    setFloatUniform("cloudDark", p.cloudDark)
    setFloatUniform("fog", p.fog)
    setFloatUniform("wind", p.wind)
    setFloatUniform("stars", p.stars)
    setFloatUniform("flash", flash)
    // The channel itself is drawn crisp over the scene ([drawLightning]), not in the soft sky.
    setFloatUniform("bolt", 0f)
    setFloatUniform("boltSeed", bolt.seed)
    setFloatUniform("boltX", bolt.x)
    setFloatUniform("flashAt", bolt.flashAt.x, bolt.flashAt.y)
    setFloatUniform("lift", p.bodyLift)
    setFloatUniform("rainbow", p.rainbow * p.bodyVisible)
    setFloatUniform("sunlitAir", p.sunlitAir * p.bodyVisible)
    setFloatUniform("diamondDust", p.diamondDust)
    setFloatUniform("cozy", p.cozy)
    setFloatUniform("tilt", tilt.x, tilt.y)
}

/** The precipitation shader's uniforms for [p] at [w] × [h] pixels. */
internal fun RuntimeShader.setPrecipitationUniforms(p: SkyParams, w: Int, h: Int, time: Float, flash: Float, tilt: Offset) {
    setFloatUniform("resolution", w.toFloat(), h.toFloat())
    setFloatUniform("time", time)
    setFloatUniform("rain", p.rain)
    setFloatUniform("snow", p.snow)
    setFloatUniform("wind", p.wind)
    setFloatUniform("flash", flash)
    setFloatUniform("tilt", tilt.x, tilt.y)
    setColorUniform("tint", lerp(p.horizon, p.cloudLight, 0.5f).toArgb())
}

private const val SUN_RADIUS = 0.022f

private val MOONLIGHT = Color(0xFFD3DCF0)

/** The cozy mood's lamp: a warm, low light in the room, and how strongly it lights the glass. */
private val LAMPLIGHT = Color(0xFFFFB46A)
private const val LAMP_POWER = 0.62f

/** Where the scene sits on screen: the sun's position is handed to the glass in root pixels. */
private class RootOrigin {
    var value = Offset.Zero
}

/** The lightning of the current flash: where it strikes, its channel, where it lights the clouds. */
internal class BoltState {
    var visible = false
    var seed = 0f
    var x = 0.5f
    var channel: LightningChannel? = null
    var flashAt = Offset(0.5f, 0.15f)
}

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
