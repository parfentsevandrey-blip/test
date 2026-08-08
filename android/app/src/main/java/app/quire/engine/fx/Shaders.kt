package app.quire.engine.fx

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.RadialGradient
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.ColorInt
import androidx.annotation.RequiresApi
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Runtime shaders where the device has them (API 33+, android.graphics.RuntimeShader with AGSL)
 * and a hand-drawn stand-in everywhere else. Every entry point must work on API 26.
 *
 * Both entry points hand back a cached, reconfigured shader rather than a fresh one, so a frame
 * that only moves [background]'s phase allocates nothing. The consequence is that the returned
 * object is only valid until the next call: set it on a Paint, draw, and let go of it. Touch
 * this from the drawing thread only — the caches are not synchronised.
 */
object Shaders {

    /**
     * Whether the AGSL path is live. False is not a failure: every call still returns a working
     * shader, drawn by hand instead.
     */
    val supported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !runtimeRefused

    // How finely the stand-in samples the phase. At this many steps one step moves no channel
    // of the buffer by more than a single 8-bit level, so the drift cannot be seen to tick,
    // and a slow turn repaints a few times a second instead of once a frame.
    private const val PHASE_STEPS: Int = 192

    // The stand-in is painted small and stretched: the pools and the vignette are smooth, and
    // a full-size buffer would cost megabytes and milliseconds to fill by hand.
    private const val STAND_IN_LONGEST: Float = 160f
    private const val MIN_SIDE: Int = 8
    private const val MAX_SIDE: Int = 256

    private const val POOL_A_RADIUS: Float = 0.85f
    private const val POOL_B_RADIUS: Float = 0.78f
    private const val VIGNETTE: Float = 0.30f
    private const val GRAIN_DEPTH: Float = 0.09f
    private const val GRAIN_SEED: Int = 4177
    private const val OPAQUE: Int = 0xFF000000.toInt()
    private const val TAU: Float = 6.2831855f

    private const val GLOW_STOPS: Int = 6

    private var runtimeRefused = false

    // Held as the base Shader type so that no field on this object names a class the platform
    // only grew in API 33; the one method that does is guarded and annotated.
    private var runtimeShader: Shader? = null

    private var standInBitmap: Bitmap? = null
    private var standInShader: BitmapShader? = null
    private var standInPixels: IntArray? = null
    private var standInGrain: FloatArray? = null
    private val standInMatrix = Matrix()
    private var cachedCols = 0
    private var cachedRows = 0
    private var cachedGrain = -1f
    private var cachedColourA = 0
    private var cachedColourB = 0
    private var cachedBase = 0
    private var cachedAspect = 0f
    private var cachedStep = -1
    private var cachedWidth = 0f
    private var cachedHeight = 0f

    private var glowGradient: RadialGradient? = null
    private var glowColour = 0
    private var glowStrength = -1f
    private val glowMatrix = Matrix()
    private val glowColours = IntArray(GLOW_STOPS)
    private val glowPositions = FloatArray(GLOW_STOPS)

    /**
     * A living background: two soft colour pools, a grain, and a vignette. [t] is a phase in
     * turns (0..1) driven by the caller, NOT by a clock inside here.
     *
     * The alpha of [colourA] and [colourB] is how strongly each pool stains [base]; the result
     * is always opaque. Null only when the size is degenerate, which is the one case where
     * there is nothing to fill.
     */
    fun background(
        width: Float,
        height: Float,
        @ColorInt colourA: Int,
        @ColorInt colourB: Int,
        @ColorInt base: Int,
        t: Float,
        grain: Float,
    ): Shader? {
        // Written as a positive test so a NaN size fails it too.
        if (!(width > 0f && height > 0f)) return null
        val phase = wrap(t)
        val amount = if (grain.isNaN()) 0f else grain.coerceIn(0f, 1f)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !runtimeRefused) {
            val live = runtimeBackground(width, height, colourA, colourB, base, phase, amount)
            if (live != null) return live
        }
        return standIn(width, height, colourA, colourB, base, phase, amount)
    }

    /** A soft radial glow usable as a Paint shader. */
    fun glow(
        cx: Float,
        cy: Float,
        radius: Float,
        @ColorInt colour: Int,
        strength: Float,
    ): Shader {
        val span = if (radius > 0f) radius else 1f
        val weight = if (strength.isNaN()) 0f else strength.coerceIn(0f, 1f)
        val existing = glowGradient
        val gradient: RadialGradient
        if (existing == null || colour != glowColour || weight != glowStrength) {
            buildGlowRamp(colour, weight)
            // Built once at unit radius about the origin and then placed with a local matrix:
            // a gradient cannot be moved after construction, and the alternative is a new one
            // every time anything on screen breathes.
            gradient = RadialGradient(
                0f,
                0f,
                1f,
                glowColours,
                glowPositions,
                Shader.TileMode.CLAMP,
            )
            glowGradient = gradient
            glowColour = colour
            glowStrength = weight
        } else {
            gradient = existing
        }
        glowMatrix.setScale(span, span)
        glowMatrix.postTranslate(cx, cy)
        gradient.setLocalMatrix(glowMatrix)
        return gradient
    }

    /** Drops cached shaders when size or palette changes. */
    fun reset() {
        standInBitmap = null
        standInShader = null
        standInPixels = null
        standInGrain = null
        cachedCols = 0
        cachedRows = 0
        cachedGrain = -1f
        cachedAspect = 0f
        cachedStep = -1
        cachedWidth = 0f
        cachedHeight = 0f
        glowGradient = null
        glowStrength = -1f
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun runtimeBackground(
        width: Float,
        height: Float,
        colourA: Int,
        colourB: Int,
        base: Int,
        phase: Float,
        grain: Float,
    ): Shader? {
        var live = runtimeShader as? RuntimeShader
        if (live == null) {
            live = try {
                RuntimeShader(AGSL_BACKGROUND)
            } catch (error: Throwable) {
                // A device whose AGSL compiler rejects this source still has to get a
                // background, so the refusal is remembered and the stand-in takes over.
                runtimeRefused = true
                null
            }
            runtimeShader = live
        }
        if (live == null) return null
        live.setFloatUniform("uSize", width, height)
        live.setFloatUniform("uPhase", phase)
        live.setFloatUniform("uGrain", grain)
        live.setFloatUniform(
            "uColourA",
            redOf(colourA),
            greenOf(colourA),
            blueOf(colourA),
            alphaOf(colourA),
        )
        live.setFloatUniform(
            "uColourB",
            redOf(colourB),
            greenOf(colourB),
            blueOf(colourB),
            alphaOf(colourB),
        )
        live.setFloatUniform(
            "uBase",
            redOf(base),
            greenOf(base),
            blueOf(base),
            alphaOf(base),
        )
        return live
    }

    private fun standIn(
        width: Float,
        height: Float,
        colourA: Int,
        colourB: Int,
        base: Int,
        phase: Float,
        grain: Float,
    ): Shader {
        // Never larger than the area it fills: a small view gets a buffer its own size, not a
        // magnified one.
        val scale = min(1f, STAND_IN_LONGEST / max(width, height))
        val cols = (width * scale).roundToInt().coerceIn(MIN_SIDE, MAX_SIDE)
        val rows = (height * scale).roundToInt().coerceIn(MIN_SIDE, MAX_SIDE)
        val step = (phase * PHASE_STEPS).toInt().coerceIn(0, PHASE_STEPS - 1)
        val aspect = width / height

        val heldBitmap = standInBitmap
        val heldPixels = standInPixels
        val bitmap: Bitmap
        val pixels: IntArray
        if (heldBitmap == null || heldPixels == null || cols != cachedCols || rows != cachedRows) {
            bitmap = Bitmap.createBitmap(cols, rows, Bitmap.Config.ARGB_8888)
            bitmap.setHasAlpha(false)
            pixels = IntArray(cols * rows)
            standInBitmap = bitmap
            standInPixels = pixels
            standInShader = null
            standInGrain = null
            cachedCols = cols
            cachedRows = rows
            cachedStep = -1
        } else {
            bitmap = heldBitmap
            pixels = heldPixels
        }

        val heldGrain = standInGrain
        val grainField: FloatArray
        if (heldGrain == null || grain != cachedGrain) {
            grainField = heldGrain ?: FloatArray(cols * rows)
            fillGrain(grainField, cols, rows, grain)
            standInGrain = grainField
            cachedGrain = grain
            cachedStep = -1
        } else {
            grainField = heldGrain
        }

        val stale = step != cachedStep || colourA != cachedColourA || colourB != cachedColourB ||
            base != cachedBase || aspect != cachedAspect
        val existing = standInShader
        val shader: BitmapShader
        if (stale || existing == null) {
            paintPools(pixels, cols, rows, aspect, colourA, colourB, base, phase, grainField)
            bitmap.setPixels(pixels, 0, cols, 0, 0, cols, rows)
            // A shader holds the bitmap, not a copy of it, but the uploaded texture is keyed
            // on the bitmap's generation and a stale one is cheaper to rule out than to debug.
            shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                shader.setFilterMode(BitmapShader.FILTER_MODE_LINEAR)
            }
            standInShader = shader
            cachedColourA = colourA
            cachedColourB = colourB
            cachedBase = base
            cachedAspect = aspect
            cachedStep = step
            cachedWidth = 0f
        } else {
            shader = existing
        }

        if (width != cachedWidth || height != cachedHeight) {
            standInMatrix.setScale(width / cols, height / rows)
            shader.setLocalMatrix(standInMatrix)
            cachedWidth = width
            cachedHeight = height
        }
        return shader
    }

    // The grain never moves. It is a fixed function of the buffer's own pixels, so a repaint
    // for a new phase leaves it untouched and it cannot pop as the pools drift.
    private fun fillGrain(field: FloatArray, cols: Int, rows: Int, grain: Float) {
        var i = 0
        var y = 0
        while (y < rows) {
            var x = 0
            while (x < cols) {
                // Two scales: a slow mottle that survives being stretched to full size, and a
                // near-per-texel speckle that breaks the banding the stretch would otherwise
                // leave across the pools.
                val mottle = Noise.fbm(x * 0.06f, y * 0.06f, 3, GRAIN_SEED)
                val speckle = Noise.value2(x * 0.83f, y * 0.83f, GRAIN_SEED + 17)
                field[i] = (mottle * 0.55f + speckle * 0.45f) * grain * GRAIN_DEPTH
                i++
                x++
            }
            y++
        }
    }

    private fun paintPools(
        pixels: IntArray,
        cols: Int,
        rows: Int,
        aspect: Float,
        colourA: Int,
        colourB: Int,
        base: Int,
        phase: Float,
        grain: FloatArray,
    ) {
        val angle = phase * TAU
        val ax = 0.30f + 0.14f * cos(angle)
        val ay = 0.32f + 0.10f * sin(angle * 1.30f + 0.70f)
        val bx = 0.72f + 0.12f * cos(angle * 0.80f + 2.10f)
        val by = 0.70f + 0.13f * sin(angle * 1.10f)
        val aRed = redOf(colourA)
        val aGreen = greenOf(colourA)
        val aBlue = blueOf(colourA)
        val aWeight = alphaOf(colourA)
        val bRed = redOf(colourB)
        val bGreen = greenOf(colourB)
        val bBlue = blueOf(colourB)
        val bWeight = alphaOf(colourB)
        val baseRed = redOf(base)
        val baseGreen = greenOf(base)
        val baseBlue = blueOf(base)

        var i = 0
        var y = 0
        while (y < rows) {
            val v = (y + 0.5f) / rows
            var x = 0
            while (x < cols) {
                val u = (x + 0.5f) / cols
                var dx = (u - ax) * aspect
                var dy = v - ay
                val wa = falloff(sqrt(dx * dx + dy * dy) / POOL_A_RADIUS) * aWeight
                dx = (u - bx) * aspect
                dy = v - by
                val wb = falloff(sqrt(dx * dx + dy * dy) / POOL_B_RADIUS) * bWeight
                var red = baseRed + (aRed - baseRed) * wa
                var green = baseGreen + (aGreen - baseGreen) * wa
                var blue = baseBlue + (aBlue - baseBlue) * wa
                red += (bRed - red) * wb
                green += (bGreen - green) * wb
                blue += (bBlue - blue) * wb
                dx = (u - 0.5f) * aspect
                dy = v - 0.5f
                val vignette = 1f - VIGNETTE * ramp(0.35f, 0.95f, sqrt(dx * dx + dy * dy))
                val speck = grain[i]
                pixels[i] = OPAQUE or
                    (channel(red * vignette + speck) shl 16) or
                    (channel(green * vignette + speck) shl 8) or
                    channel(blue * vignette + speck)
                i++
                x++
            }
            y++
        }
    }

    private fun buildGlowRamp(colour: Int, strength: Float) {
        val rgb = colour and 0x00FFFFFF
        val source = alphaOf(colour)
        var i = 0
        while (i < GLOW_STOPS) {
            val at = i / (GLOW_STOPS - 1f)
            glowPositions[i] = at
            // (1 - r^2)^2 falls off flat at the centre and flat at the rim, which is what a
            // light looks like; a straight ramp shows its edge as a ring.
            val k = 1f - at * at
            val a = (k * k * strength * source * 255f + 0.5f).toInt().coerceIn(0, 255)
            glowColours[i] = rgb or (a shl 24)
            i++
        }
    }

    private fun wrap(t: Float): Float {
        if (t.isNaN()) return 0f
        return t - floor(t)
    }

    private fun falloff(k: Float): Float {
        val f = 1f - k.coerceIn(0f, 1f)
        return f * f * (3f - 2f * f)
    }

    private fun ramp(from: Float, to: Float, x: Float): Float {
        val t = ((x - from) / (to - from)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun channel(value: Float): Int =
        (value * 255f + 0.5f).toInt().coerceIn(0, 255)
}

private fun alphaOf(colour: Int): Float = ((colour ushr 24) and 0xFF) / 255f

private fun redOf(colour: Int): Float = ((colour ushr 16) and 0xFF) / 255f

private fun greenOf(colour: Int): Float = ((colour ushr 8) and 0xFF) / 255f

private fun blueOf(colour: Int): Float = (colour and 0xFF) / 255f

// Colours arrive as plain float4 rather than layout(color) uniforms: the app is sRGB end to
// end, so there is no conversion to ask the runtime for, and one less thing to get wrong on a
// device with an unusual working colour space.
private const val AGSL_BACKGROUND = """
uniform float2 uSize;
uniform float uPhase;
uniform float uGrain;
uniform float4 uColourA;
uniform float4 uColourB;
uniform float4 uBase;

float pool(float2 uv, float2 centre, float radius, float aspect) {
    float2 d = float2((uv.x - centre.x) * aspect, uv.y - centre.y);
    float k = clamp(1.0 - length(d) / radius, 0.0, 1.0);
    return k * k * (3.0 - 2.0 * k);
}

float speck(float2 p) {
    return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453);
}

half4 main(float2 fragCoord) {
    float2 uv = float2(fragCoord.x / uSize.x, fragCoord.y / uSize.y);
    float aspect = uSize.x / uSize.y;
    float a = uPhase * 6.2831853;
    float2 ca = float2(0.30 + 0.14 * cos(a), 0.32 + 0.10 * sin(a * 1.30 + 0.70));
    float2 cb = float2(0.72 + 0.12 * cos(a * 0.80 + 2.10), 0.70 + 0.13 * sin(a * 1.10));
    float3 col = uBase.rgb;
    col = mix(col, uColourA.rgb, pool(uv, ca, 0.85, aspect) * uColourA.a);
    col = mix(col, uColourB.rgb, pool(uv, cb, 0.78, aspect) * uColourB.a);
    float2 v = float2((uv.x - 0.5) * aspect, uv.y - 0.5);
    col = col * (1.0 - 0.30 * smoothstep(0.35, 0.95, length(v)));
    float g = (speck(floor(fragCoord)) - 0.5) * uGrain * 0.10;
    col = clamp(col + float3(g, g, g), 0.0, 1.0);
    return half4(col, 1.0);
}
"""
