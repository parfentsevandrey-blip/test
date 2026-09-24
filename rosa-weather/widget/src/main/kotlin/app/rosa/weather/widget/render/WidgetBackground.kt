package app.rosa.weather.widget.render

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import androidx.core.graphics.withClip
import app.rosa.weather.core.designsystem.glyph.WeatherGlyphPainter
import app.rosa.weather.core.model.Argb
import app.rosa.weather.core.model.WeatherCondition
import app.rosa.weather.core.model.WeatherVisual
import app.rosa.weather.core.model.WidgetConfig
import app.rosa.weather.core.model.WidgetStyle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Where the sun or moon sits inside the widget, 0..1 on both axes (y grows downward), and where it
 * really is in the sky ([elevation], [azimuth], degrees).
 */
data class SkyAnchor(
    val x: Float,
    val y: Float,
    val isSun: Boolean,
    val elevation: Double,
    val moonPhase: Double,
    val azimuth: Double = 180.0,
)

/**
 * Paints widget panes. Launchers can't run a backdrop shader, so Liquid Glass is approximated
 * the way Apple's own "clear" widgets are built: a translucent tinted body, a bright lensing rim,
 * a darker hairline edge, inner edge glow, and a faint ambient light spilling from the sun or
 * moon. As in the app, the glass is lit by the sky it shows ([WidgetLight]): the rim catches the
 * light on the side of the sun or moon, in its colour, a glint flares where it faces the sun, and
 * the world's reflections lie across the pane.
 */
internal class WidgetBackground {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val glyphs = WeatherGlyphPainter()
    private val weather = WidgetWeather()

    fun draw(
        canvas: Canvas,
        w: Float,
        h: Float,
        radius: Float,
        config: WidgetConfig,
        palette: WidgetPalette,
        visual: WeatherVisual,
        anchor: SkyAnchor,
        dynamic: DynamicTones,
        seed: Int,
        pane: Pane = Pane.Dry,
        live: Boolean = false,
        light: WidgetLight = WidgetLight.Resting,
    ) {
        val rect = RectF(0f, 0f, w, h)
        path.reset()
        path.addRoundRect(rect, radius, radius, Path.Direction.CW)
        this.pane = pane
        this.light = light
        outline = PaneRim(rect, radius)
        weather.live = live
        when (config.style) {
            WidgetStyle.Glass -> glass(canvas, rect, radius, config, palette, visual, anchor, seed)
            WidgetStyle.Sky -> sky(canvas, rect, radius, config, palette, visual, anchor, seed)
            WidgetStyle.Clear -> {
                // Over the wallpaper: rain and beads with nothing known behind them to refract.
                if (config.showWeatherArt) {
                    canvas.withClip(path) {
                        weather.behind(this, rect, visual, Argb.White, strength = 0.7f, seed = seed)
                        weather.onGlass(this, rect, radius, visual, pane.frost, 0f, Argb.White, Argb.White, palette.isDark, refract = false, seed = seed)
                    }
                }
                if (config.glassRim) {
                    canvas.withClip(path) { reflections(this, rect, palette.isDark, seed, strength = 0.6f) }
                    glassBezel(canvas, rect, radius, palette, strength = 0.65f)
                }
            }
            WidgetStyle.Tonal -> {
                val (a, b) = tonal(canvas, rect, radius, config, palette, dynamic)
                if (config.showWeatherArt) {
                    canvas.withClip(path) {
                        weather.behind(this, rect, visual, a.lerp(Argb.White, 0.6f), strength = 0.5f, seed = seed)
                        weather.onGlass(this, rect, radius, visual, pane.frost, pane.mist * 0.6f, a, b, palette.isDark, refract = true, seed = seed)
                    }
                }
                if (config.glassRim) {
                    canvas.withClip(path) { reflections(this, rect, palette.isDark, seed, strength = 0.6f) }
                    glassBezel(canvas, rect, radius, palette, strength = 0.7f)
                }
            }
            WidgetStyle.Paper -> paper(canvas, rect, radius, palette)
        }
    }

    /** The state of the widget's glass at this moment, as the app's window shows it. */
    data class Pane(val frost: Float, val mist: Float) {
        companion object {
            val Dry = Pane(0f, 0f)
        }
    }

    private var pane = Pane.Dry
    private var light = WidgetLight.Resting

    /** The pane's outline, sampled for the light laid along it. */
    private var outline: PaneRim? = null

    // region Glass

    private fun glass(
        canvas: Canvas, rect: RectF, radius: Float, config: WidgetConfig, palette: WidgetPalette,
        visual: WeatherVisual, anchor: SkyAnchor, seed: Int,
    ) {
        val dark = palette.isDark
        val opacity = config.opacity.coerceIn(0.15f, 1f)
        val base = if (dark) Argb.hex(0x0D111E) else Argb.hex(0xF3F5FA)
        val top = palette.sky.zenith.lerp(base, if (dark) 0.35f else 0.62f)
        val bottom = palette.sky.horizon.lerp(base, if (dark) 0.45f else 0.7f)

        canvas.withClip(path) { glassBody(this, rect, radius, config, palette, visual, anchor, seed, dark, opacity, top, bottom) }
        if (config.glassRim) glassBezel(canvas, rect, radius, palette, strength = 1f) else rim(canvas, rect, radius, palette, strength = 1f)
    }

    private fun glassBody(
        canvas: Canvas, rect: RectF, radius: Float, config: WidgetConfig, palette: WidgetPalette, visual: WeatherVisual,
        anchor: SkyAnchor, seed: Int, dark: Boolean, opacity: Float, top: Argb, bottom: Argb,
    ) {
        // Body: sky-tinted, translucent.
        paint.color = 0xFFFFFFFF.toInt()
        paint.shader = LinearGradient(
            0f, 0f, 0f, rect.height(),
            top.withAlpha(opacity * if (dark) 0.78f else 0.7f).value,
            bottom.withAlpha(opacity * if (dark) 0.7f else 0.62f).value,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(rect, paint)

        if (config.showWeatherArt) {
            ambientLight(canvas, rect, palette, anchor, opacity)
            weather.behind(canvas, rect, visual, palette.sky.horizon.lerp(Argb.White, 0.55f), strength = 0.55f + 0.3f * opacity, seed = seed)
            if (!anchor.isSun && visual.cloudCover < 0.6f) stars(canvas, rect, (1f - visual.cloudCover) * 0.5f, seed)
        }

        // Top sheen: light entering the glass from above.
        paint.color = 0xFFFFFFFF.toInt()
        paint.shader = LinearGradient(
            0f, 0f, 0f, rect.height() * 0.55f,
            Argb.White.withAlpha(if (dark) 0.07f else 0.2f).value, 0x00FFFFFF, Shader.TileMode.CLAMP,
        )
        canvas.drawRect(rect, paint)
        grain(canvas, rect, if (dark) 0.035f else 0.045f)
        if (config.showWeatherArt) {
            weather.onGlass(canvas, rect, radius, visual, pane.frost, pane.mist, top, bottom, dark, refract = true, seed = seed)
        }
        reflections(canvas, rect, dark, seed, strength = 1f)
        if (!config.glassRim) innerGlow(canvas, rect, radius, dark)
    }

    private fun ambientLight(canvas: Canvas, rect: RectF, palette: WidgetPalette, anchor: SkyAnchor, opacity: Float) {
        val cx = rect.width() * anchor.x
        val cy = rect.height() * anchor.y.coerceIn(-0.2f, 1.1f)
        val r = max(rect.width(), rect.height()) * 0.9f
        val strength = if (anchor.isSun) 0.55f else 0.28f
        paint.color = 0xFFFFFFFF.toInt()
        paint.shader = RadialGradient(
            cx, cy, r,
            intArrayOf(palette.sky.glow.withAlpha(strength * opacity).value, palette.sky.glow.withAlpha(strength * 0.35f * opacity).value, 0),
            floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP,
        )
        canvas.drawRect(rect, paint)
    }

    /** Lensing rim: a thin bright edge, brightest where it faces the light and, less, across from it. */
    private fun rim(canvas: Canvas, rect: RectF, radius: Float, palette: WidgetPalette, strength: Float) {
        val inset = 0.6f
        val r = RectF(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset)
        // The highlight colour borrows from the saturated backdrop, as Apple's vibrant rim does,
        // and from the light itself.
        val tint = palette.sky.glow.lerp(Argb.White, 0.55f).lerp(light.color, light.share)
        val lit = strength * (0.85f + 0.35f * light.power)
        paint.color = 0xFFFFFFFF.toInt()
        paint.shader = rimLight(tint, peak = 0.95f * lit, side = 0.1f * lit)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.1f
        canvas.drawRoundRect(r, radius - inset, radius - inset, paint)
        // Darkened outer hairline keeps the pane crisp on bright wallpapers.
        paint.shader = null
        paint.color = if (palette.isDark) 0x2E000000 else 0x1F000000
        paint.strokeWidth = 0.6f
        canvas.drawRoundRect(RectF(rect.left + 0.3f, rect.top + 0.3f, rect.right - 0.3f, rect.bottom - 0.3f), radius, radius, paint)
        paint.style = Paint.Style.FILL
    }

    /**
     * The glass edge: a thick slab's curved rim shaded by the scene's light ([GlassBevel]) — a
     * catch-light all the way round, brightest where it faces the light, a finer streak inside
     * it, the sky's reflection along the top and the far side in shadow — and in real sunlight a
     * glint. A darker outer hairline under it keeps the pane crisp on bright wallpapers.
     */
    private fun glassBezel(canvas: Canvas, rect: RectF, radius: Float, palette: WidgetPalette, strength: Float) {
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.color = if (palette.isDark) 0x2E000000 else 0x1F000000
        paint.strokeWidth = 0.6f
        canvas.drawRoundRect(RectF(rect.left + 0.3f, rect.top + 0.3f, rect.right - 0.3f, rect.bottom - 0.3f), radius, radius, paint)
        paint.style = Paint.Style.FILL
        GlassBevel.draw(canvas, rect, radius, light, palette.isDark, strength)
        glint(canvas, rect, strength)
    }

    /**
     * Light around the rim from the sun or moon and from the sky, as much of each as [WidgetLight.share]
     * gives: [peak] where the rim faces a light, fading gradually along the sides away from there,
     * a weaker twin across the pane (weaker still in real sunlight), [side] along the sides, and
     * nothing where the rim runs parallel to the light.
     */
    private fun rimLight(color: Argb, peak: Float, side: Float): SweepGradient {
        val twin = 0.7f - 0.3f * light.power
        val share = light.share
        val sx = cos(light.angle)
        val sy = sin(light.angle)
        val rx = cos(WidgetLight.RESTING_ANGLE)
        val ry = sin(WidgetLight.RESTING_ANGLE)
        fun lobe(facing: Float, toward: Float): Float {
            val lit = 0.7f * max(facing, 0f).pow(10) + 0.3f * max(toward, 0f).pow(6)
            val back = 0.7f * max(-facing, 0f).pow(10) + 0.3f * max(-toward, 0f).pow(6)
            return peak * (lit + twin * back) + side * abs(facing)
        }
        return outline!!.sweep(color) { nx, ny, dx, dy ->
            share * lobe(nx * sx + ny * sy, dx * sx + dy * sy) + (1f - share) * lobe(nx * rx + ny * ry, dx * rx + dy * ry)
        }
    }

    /**
     * Where the rim faces the sun squarely it flares: a hot core, a streak along the edge and a
     * shorter spike across it, blooming a little past the glass. Only real light makes one.
     */
    private fun glint(canvas: Canvas, rect: RectF, strength: Float) {
        val rim = outline ?: return
        val power = light.power * strength
        if (power < 0.08f) return
        val at = rim.facing(light.angle)
        val nx = rim.nx[at]
        val ny = rim.ny[at]
        val x = rim.x[at] - nx * 0.8f
        val y = rim.y[at] - ny * 0.8f
        val k = (min(rect.width(), rect.height()) / 150f).coerceIn(0.55f, 1.1f)
        val hot = light.color
        paint.style = Paint.Style.FILL
        paint.maskFilter = null
        paint.color = 0xFFFFFFFF.toInt()
        val bloom = 15f * k
        paint.shader = RadialGradient(
            x, y, bloom,
            intArrayOf(hot.withAlpha(0.4f * power).value, hot.withAlpha(0.1f * power).value, 0),
            floatArrayOf(0f, 0.3f, 1f), Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(x, y, bloom, paint)
        streak(canvas, x, y, -ny, nx, 17f * k, 1.1f, 0.85f * power, hot)
        streak(canvas, x, y, nx, ny, 6.5f * k, 0.9f, 0.5f * power, hot)
        val core = 2.6f * k
        paint.color = 0xFFFFFFFF.toInt()
        paint.shader = RadialGradient(
            x, y, core,
            intArrayOf(Argb.White.withAlpha(0.95f * power).value, Argb.White.withAlpha(0.45f * power).value, 0),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(x, y, core, paint)
        paint.shader = null
    }

    /** A thin ray of light through ([x], [y]) along ([dx], [dy]), fading toward both ends. */
    private fun streak(canvas: Canvas, x: Float, y: Float, dx: Float, dy: Float, reach: Float, width: Float, alpha: Float, color: Argb) {
        paint.color = 0xFFFFFFFF.toInt()
        paint.shader = LinearGradient(
            x - dx * reach, y - dy * reach, x + dx * reach, y + dy * reach,
            intArrayOf(0, color.withAlpha(alpha).value, 0), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
        )
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = width
        paint.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(x - dx * reach, y - dy * reach, x + dx * reach, y + dy * reach, paint)
        paint.strokeCap = Paint.Cap.BUTT
        paint.style = Paint.Style.FILL
        paint.shader = null
    }

    /**
     * Reflections of the world on the pane: a soft band of a window's light and a thin one beside
     * it, lying across the glass as they do across the app's.
     */
    private fun reflections(canvas: Canvas, rect: RectF, dark: Boolean, seed: Int, strength: Float) {
        val period = 560f
        val dx = 0.55f
        val dy = 0.83f
        // The main band crosses somewhere in the upper-left half of the pane, a little differently on each widget.
        val extent = rect.width() * dx + rect.height() * dy
        val jitter = ((seed * 0.618034f) % 1f + 1f) % 1f
        val offset = extent * (0.22f + 0.16f * jitter) - 0.32f * period
        val tone = Argb.White.lerp(light.sky, 0.3f)
        val peak = 0.05f * strength * (if (dark) 0.55f else 1f)
        val steps = 160
        val colors = IntArray(steps + 1) { j ->
            val phase = j / steps.toFloat()
            val main = (phase - 0.32f) / 0.07f
            val thin = (phase - 0.43f) / 0.018f
            tone.withAlpha(peak * (exp(-main * main) + 0.55f * exp(-thin * thin))).value
        }
        paint.color = 0xFFFFFFFF.toInt()
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(dx * offset, dy * offset, dx * (offset + period), dy * (offset + period), colors, null, Shader.TileMode.REPEAT)
        canvas.drawRect(rect, paint)
        paint.shader = null
    }

    /** Soft brightening just inside the edge, the visual cue of a thick, curved bezel. */
    private fun innerGlow(canvas: Canvas, rect: RectF, radius: Float, dark: Boolean) {
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 7f
        paint.color = if (dark) 0x16FFFFFF else 0x30FFFFFF
        paint.maskFilter = BlurMaskFilter(5f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.maskFilter = null
        paint.style = Paint.Style.FILL
    }

    // endregion

    // region Sky

    private fun sky(
        canvas: Canvas, rect: RectF, radius: Float, config: WidgetConfig, palette: WidgetPalette,
        visual: WeatherVisual, anchor: SkyAnchor, seed: Int,
    ) {
        val w = rect.width()
        val h = rect.height()
        val opacity = config.opacity.coerceIn(0.3f, 1f)
        canvas.saveLayerAlpha(rect, (opacity * 255).toInt())
        canvas.clipPath(path)
        val sky = palette.sky
        paint.color = 0xFFFFFFFF.toInt()
        paint.shader = LinearGradient(0f, 0f, 0f, h, sky.zenith.value, sky.horizon.value, Shader.TileMode.CLAMP)
        canvas.drawRect(rect, paint)

        // Horizon glow toward the sun's side (sunsets bloom, noon stays clean).
        val glowStrength = if (anchor.isSun) (1f - (anchor.elevation / 35.0).toFloat().coerceIn(0f, 1f)) * 0.8f + 0.15f else 0.2f
        paint.color = 0xFFFFFFFF.toInt()
        paint.shader = RadialGradient(
            w * anchor.x, h * 1.05f, max(w, h) * 0.95f,
            intArrayOf(sky.glow.withAlpha(glowStrength).value, sky.glow.withAlpha(glowStrength * 0.3f).value, 0),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP,
        )
        canvas.drawRect(rect, paint)

        if (!anchor.isSun) stars(canvas, rect, (1f - visual.cloudCover) * ((-anchor.elevation - 4) / 10.0).toFloat().coerceIn(0f, 1f), seed)
        celestialBody(canvas, rect, anchor, palette, visual)
        clouds(canvas, rect, palette, visual, seed)
        fog(canvas, rect, palette, visual)
        weather.behind(canvas, rect, visual, sky.horizon.lerp(Argb.White, 0.55f), strength = 1f, seed = seed)
        if (visual.lightning > 0f && !weather.live) lightning(canvas, rect, seed)

        // Legibility: light type gets a dimmed, sky-coloured veil, dark type a milky one — like
        // Apple's advice to dim bright content under clear glass (≈35%).
        paint.shader = null
        paint.color = if (palette.isDark) 0x2A0A0E1A else 0x24FFFFFF
        canvas.drawRect(rect, paint)
        paint.color = 0xFFFFFFFF.toInt()
        paint.shader = LinearGradient(
            0f, 0f, w * 0.7f, h * 0.8f,
            (if (palette.isDark) sky.zenith.lerp(Argb.Black, 0.3f) else sky.horizon.lerp(Argb.White, 0.4f)).withAlpha(0.42f).value,
            0, Shader.TileMode.CLAMP,
        )
        canvas.drawRect(rect, paint)
        grain(canvas, rect, 0.035f)
        if (config.showWeatherArt) {
            weather.onGlass(canvas, rect, radius, visual, pane.frost, pane.mist, sky.zenith, sky.horizon.lerp(sky.glow, 0.25f), palette.isDark, refract = true, seed = seed)
        }
        reflections(canvas, rect, palette.isDark, seed, strength = 0.8f)
        canvas.restore()
        if (config.glassRim) glassBezel(canvas, rect, radius, palette, strength = 0.85f) else rim(canvas, rect, radius, palette, strength = 0.75f)
    }

    private fun celestialBody(canvas: Canvas, rect: RectF, anchor: SkyAnchor, palette: WidgetPalette, visual: WeatherVisual) {
        val w = rect.width()
        val h = rect.height()
        val cx = w * anchor.x
        val cy = h * anchor.y
        val size = min(w, h)
        // Scattered cloud veils the sun or moon; an overcast sky hides it, as it hides its light.
        val overcast = ((visual.cloudCover - 0.3f) / 0.65f).coerceIn(0f, 1f)
        val veil = 1f - overcast * overcast * (3f - 2f * overcast)
        if (veil < 0.02f) return
        if (anchor.isSun) {
            if (cy > h * 1.15f) return
            val r = size * 0.075f
            paint.color = 0xFFFFFFFF.toInt()
            paint.shader = RadialGradient(
                cx, cy, r * 7f,
                intArrayOf(palette.sky.sun.withAlpha(0.85f * veil).value, palette.sky.sun.withAlpha(0.22f * veil).value, 0),
                floatArrayOf(0f, 0.25f, 1f), Shader.TileMode.CLAMP,
            )
            canvas.drawCircle(cx, cy, r * 7f, paint)
            paint.color = 0xFFFFFFFF.toInt()
            paint.shader = RadialGradient(cx, cy, r, intArrayOf(Argb.White.withAlpha(veil).value, palette.sky.sun.withAlpha(veil).value), null, Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, cy, r, paint)
            paint.shader = null
        } else if (anchor.elevation > -2) {
            val r = size * 0.24f
            canvas.saveLayerAlpha(RectF(cx - r, cy - r, cx + r, cy + r), (veil * 255).toInt())
            glyphs.draw(canvas, WeatherCondition.Clear, isDay = false, bounds = RectF(cx - r, cy - r, cx + r, cy + r), moonPhase = anchor.moonPhase)
            canvas.restore()
        }
    }

    private fun clouds(canvas: Canvas, rect: RectF, palette: WidgetPalette, visual: WeatherVisual, seed: Int) {
        if (visual.cloudCover < 0.08f) return
        val w = rect.width()
        val h = rect.height()
        val rnd = Random(seed * 31 + 7)
        val count = (2 + visual.cloudCover * 7).toInt()
        val blur = min(w, h) * 0.045f
        paint.maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
        repeat(count) { i ->
            val cx = rnd.nextFloat() * w * 1.2f - w * 0.1f
            val cy = h * (0.12f + rnd.nextFloat() * (0.35f + visual.cloudCover * 0.45f))
            val cw = w * (0.28f + rnd.nextFloat() * 0.35f) * (0.7f + visual.cloudCover * 0.5f)
            val ch = cw * (0.28f + rnd.nextFloat() * 0.12f)
            val depth = i.toFloat() / count
            val lightness = (0.2f + depth * 0.45f) * (1f - visual.cloudDarkness * 0.6f)
            val shade = palette.sky.cloudShade.lerp(palette.sky.cloudLight, lightness)
            paint.shader = null
            paint.color = shade.withAlpha(0.4f + visual.cloudCover * 0.3f).value
            canvas.drawOval(RectF(cx - cw / 2, cy - ch * 0.2f, cx + cw / 2, cy + ch * 0.8f), paint)
            paint.color = palette.sky.cloudShade.lerp(palette.sky.cloudLight, lightness + 0.25f)
                .withAlpha(0.3f + visual.cloudCover * 0.3f).value
            repeat(3) { b ->
                val bx = cx - cw * 0.3f + b * cw * 0.3f
                val br = ch * (0.55f + rnd.nextFloat() * 0.35f)
                canvas.drawCircle(bx, cy, br, paint)
            }
        }
        paint.maskFilter = null
    }

    private fun fog(canvas: Canvas, rect: RectF, palette: WidgetPalette, visual: WeatherVisual) {
        if (visual.fog < 0.1f) return
        val w = rect.width()
        val h = rect.height()
        paint.maskFilter = BlurMaskFilter(h * 0.08f, BlurMaskFilter.Blur.NORMAL)
        paint.shader = null
        val pearl = palette.sky.horizon.lerp(Argb.White, 0.35f)
        for (i in 0 until 3) {
            paint.color = pearl.withAlpha(visual.fog * (0.35f + i * 0.12f)).value
            val y = h * (0.45f + i * 0.2f)
            canvas.drawRect(-w * 0.1f, y, w * 1.1f, y + h * 0.16f, paint)
        }
        paint.maskFilter = null
    }

    private fun stars(canvas: Canvas, rect: RectF, strength: Float, seed: Int) {
        if (strength <= 0.02f) return
        val rnd = Random(seed * 13 + 1)
        val n = (rect.width() * rect.height() / 650f).toInt().coerceIn(10, 90)
        paint.shader = null
        repeat(n) {
            val x = rnd.nextFloat() * rect.width()
            val y = rnd.nextFloat() * rect.height() * 0.85f
            val r = 0.35f + rnd.nextFloat() * rnd.nextFloat() * 1.1f
            paint.color = Argb.White.withAlpha(strength * (0.3f + rnd.nextFloat() * 0.7f)).value
            canvas.drawCircle(x, y, r, paint)
        }
    }

    private fun lightning(canvas: Canvas, rect: RectF, seed: Int) {
        val rnd = Random(seed * 5 + 11)
        val w = rect.width()
        val h = rect.height()
        var x = w * (0.55f + rnd.nextFloat() * 0.3f)
        var y = h * 0.1f
        val bolt = Path().apply { moveTo(x, y) }
        while (y < h * 0.7f) {
            x += (rnd.nextFloat() - 0.5f) * w * 0.09f
            y += h * (0.06f + rnd.nextFloat() * 0.06f)
            bolt.lineTo(x, y)
        }
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = 0x55D8CCFF
        paint.strokeWidth = 4f
        paint.maskFilter = BlurMaskFilter(4f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawPath(bolt, paint)
        paint.maskFilter = null
        paint.color = 0xCCFFFFFF.toInt()
        paint.strokeWidth = 1.1f
        canvas.drawPath(bolt, paint)
        paint.style = Paint.Style.FILL
    }

    // endregion

    // region Tonal & paper

    /** Draws the tonal body; returns its colours at the top-left and bottom-right. */
    private fun tonal(canvas: Canvas, rect: RectF, radius: Float, config: WidgetConfig, palette: WidgetPalette, dynamic: DynamicTones): Pair<Argb, Argb> {
        val opacity = config.opacity.coerceIn(0.2f, 1f).let { 0.55f + it * 0.45f }
        val (a, b) = if (palette.isDark) {
            Argb(dynamic.neutralDark).lerp(Argb(dynamic.accentDark), 0.35f) to Argb(dynamic.accentDark).lerp(Argb(dynamic.neutralDark), 0.4f)
        } else {
            Argb(dynamic.accentLight) to Argb(dynamic.neutralLight).lerp(Argb(dynamic.accentLight), 0.45f)
        }
        paint.color = 0xFFFFFFFF.toInt()
        paint.shader = LinearGradient(0f, 0f, rect.width(), rect.height(), a.withAlpha(opacity).value, b.withAlpha(opacity).value, Shader.TileMode.CLAMP)
        canvas.drawPath(path, paint)
        paint.shader = null
        return a to b
    }

    private fun paper(canvas: Canvas, rect: RectF, radius: Float, palette: WidgetPalette) {
        val dark = palette.isDark
        canvas.withClip(path) { paperBody(this, rect, dark) }
        paperFrame(canvas, rect, radius, palette)
    }

    private fun paperBody(canvas: Canvas, rect: RectF, dark: Boolean) {
        paint.color = 0xFFFFFFFF.toInt()
        paint.shader = LinearGradient(
            0f, 0f, rect.width() * 0.3f, rect.height(),
            if (dark) 0xFF26231F.toInt() else 0xFFF8F1E4.toInt(),
            if (dark) 0xFF1B1916.toInt() else 0xFFEEE2CD.toInt(),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(rect, paint)
        // Warm vignette like light falling on a page.
        paint.color = 0xFFFFFFFF.toInt()
        paint.shader = RadialGradient(
            rect.width() * 0.3f, rect.height() * 0.2f, max(rect.width(), rect.height()) * 1.1f,
            intArrayOf(0x00000000, if (dark) 0x40000000 else 0x1A5A3A10),
            floatArrayOf(0.45f, 1f), Shader.TileMode.CLAMP,
        )
        canvas.drawRect(rect, paint)
        grain(canvas, rect, if (dark) 0.06f else 0.09f)
    }

    private fun paperFrame(canvas: Canvas, rect: RectF, radius: Float, palette: WidgetPalette) {
        // Printed frame.
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.7f
        paint.color = WidgetPalette.withAlpha(palette.ink, 0.16f)
        val inset = min(rect.width(), rect.height()) * 0.035f + 2f
        val inner = RectF(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset)
        canvas.drawRoundRect(inner, max(0f, radius - inset), max(0f, radius - inset), paint)
        paint.style = Paint.Style.FILL
    }

    // endregion

    private fun grain(canvas: Canvas, rect: RectF, alpha: Float) {
        paint.color = 0xFFFFFFFF.toInt()
        paint.shader = noiseShader
        paint.alpha = (alpha * 255).toInt()
        canvas.drawRect(rect, paint)
        paint.alpha = 255
        paint.shader = null
    }

    companion object {
        private val noiseShader: BitmapShader by lazy {
            val size = 96
            val rnd = Random(1234)
            val pixels = IntArray(size * size) {
                val v = rnd.nextInt(256)
                val a = rnd.nextInt(256)
                (a shl 24) or (v shl 16) or (v shl 8) or v
            }
            val bmp = Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
            BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        }

        /** Maps the sun/moon's true position to widget space: east on the left, zenith at the top. */
        fun anchorFor(elevation: Double, azimuth: Double, isSun: Boolean, moonPhase: Double): SkyAnchor {
            val x = (((azimuth - 90.0) / 180.0).toFloat()).coerceIn(0.08f, 0.92f)
            val y = (1f - ((elevation + 6.0) / 60.0).toFloat()).coerceIn(0.1f, 1.2f)
            return SkyAnchor(x, y, isSun, elevation, moonPhase, azimuth)
        }
    }
}
