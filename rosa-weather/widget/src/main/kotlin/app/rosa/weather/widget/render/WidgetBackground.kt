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
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** Where the sun or moon sits inside the widget, 0..1 on both axes (y grows downward). */
data class SkyAnchor(val x: Float, val y: Float, val isSun: Boolean, val elevation: Double, val moonPhase: Double)

/**
 * Paints widget panes. Launchers can't run a backdrop shader, so Liquid Glass is approximated
 * the way Apple's own "clear" widgets are built: a translucent tinted body, a bright lensing rim
 * with the signature pair of diagonal specular highlights (45° / −135°), a darker hairline edge,
 * inner edge glow, and a faint ambient light spilling from the sun or moon.
 */
internal class WidgetBackground {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val glyphs = WeatherGlyphPainter()

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
    ) {
        val rect = RectF(0f, 0f, w, h)
        path.reset()
        path.addRoundRect(rect, radius, radius, Path.Direction.CW)
        when (config.style) {
            WidgetStyle.Glass -> glass(canvas, rect, radius, config, palette, visual, anchor, seed)
            WidgetStyle.Sky -> sky(canvas, rect, radius, config, palette, visual, anchor, seed)
            WidgetStyle.Clear -> if (config.glassRim) glassBezel(canvas, rect, radius, palette, strength = 0.65f)
            WidgetStyle.Tonal -> {
                tonal(canvas, rect, radius, config, palette, dynamic)
                if (config.glassRim) glassBezel(canvas, rect, radius, palette, strength = 0.7f)
            }
            WidgetStyle.Paper -> paper(canvas, rect, radius, palette)
        }
    }

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
            precipitation(canvas, rect, visual, alpha = 0.22f, seed = seed)
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
        innerGlow(canvas, rect, radius, dark)
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

    /** Lensing rim: thin bright edge brightest at the top-left and bottom-right corners. */
    private fun rim(canvas: Canvas, rect: RectF, radius: Float, palette: WidgetPalette, strength: Float) {
        val inset = 0.6f
        val r = RectF(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset)
        // The highlight colour borrows from the saturated backdrop, as Apple's vibrant rim does.
        val tint = palette.sky.glow.lerp(Argb.White, 0.55f)
        paint.color = 0xFFFFFFFF.toInt()
        paint.shader = LinearGradient(
            rect.left, rect.top, rect.right, rect.bottom,
            intArrayOf(
                tint.withAlpha(0.95f * strength).value,
                tint.withAlpha(0.18f * strength).value,
                tint.withAlpha(0.06f * strength).value,
                tint.withAlpha(0.18f * strength).value,
                tint.withAlpha(0.7f * strength).value,
            ),
            floatArrayOf(0f, 0.22f, 0.5f, 0.78f, 1f), Shader.TileMode.CLAMP,
        )
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
     * A thick glass edge. Launchers can't refract the wallpaper, so the bezel is painted the way
     * light behaves on one: a Fresnel band welling up inside the edge, brightest on the lit
     * corners (Apple's 45° / −135° pair); a faint shadow band where the glass turns away; warm and
     * cool dispersion fringes a hair apart; a crisp rim line and two specular glints.
     */
    private fun glassBezel(canvas: Canvas, rect: RectF, radius: Float, palette: WidgetPalette, strength: Float) {
        val bevel = (min(rect.width(), rect.height()) * 0.08f).coerceIn(6f, 14f)
        val cx = rect.centerX()
        val cy = rect.centerY()
        val light = palette.sky.glow.lerp(Argb.White, 0.65f)
        canvas.withClip(path) {
            paint.style = Paint.Style.STROKE
            // Fresnel band.
            paint.shader = litSweep(cx, cy, light, peak = 0.92f * strength, side = 0.4f * strength, low = 0.1f * strength)
            paint.strokeWidth = bevel * 2f
            paint.maskFilter = BlurMaskFilter(bevel * 0.7f, BlurMaskFilter.Blur.NORMAL)
            drawRoundRect(rect, radius, radius, paint)
            // Sharper light right at the edge, where the bevel is steepest.
            paint.strokeWidth = bevel * 0.8f
            paint.maskFilter = BlurMaskFilter(bevel * 0.25f, BlurMaskFilter.Blur.NORMAL)
            drawRoundRect(rect, radius, radius, paint)
            // Thickness: the inner edge of the bevel, shaded most where no light reaches.
            val inner = RectF(rect.left + bevel, rect.top + bevel, rect.right - bevel, rect.bottom - bevel)
            val innerRadius = max(0f, radius - bevel)
            paint.shader = litSweep(cx, cy, Argb.Black, peak = 0.03f * strength, side = 0.1f * strength, low = 0.22f * strength)
            paint.strokeWidth = bevel * 0.9f
            paint.maskFilter = BlurMaskFilter(bevel * 0.6f, BlurMaskFilter.Blur.NORMAL)
            drawRoundRect(inner, innerRadius, innerRadius, paint)
            paint.maskFilter = null
            // Where the curved bevel meets the flat face: the second edge of a glass slab.
            val face = RectF(rect.left + bevel * 0.95f, rect.top + bevel * 0.95f, rect.right - bevel * 0.95f, rect.bottom - bevel * 0.95f)
            val faceRadius = max(0f, radius - bevel * 0.95f)
            paint.shader = litSweep(cx, cy, light, peak = 0.55f * strength, side = 0.2f * strength, low = 0.05f * strength)
            paint.strokeWidth = 0.9f
            drawRoundRect(face, faceRadius, faceRadius, paint)
            // Dispersion fringes.
            paint.strokeWidth = 1f
            for ((inset, color, alpha) in listOf(Triple(1.8f, Argb.hex(0x7FE6FF), 0.62f), Triple(3f, Argb.hex(0xFFA6D8), 0.48f))) {
                val r = RectF(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset)
                paint.shader = litSweep(cx, cy, color, peak = alpha * strength, side = alpha * 0.25f * strength, low = 0f)
                drawRoundRect(r, radius - inset, radius - inset, paint)
            }
        }
        // Crisp rim line.
        paint.style = Paint.Style.STROKE
        val inset = 0.6f
        paint.shader = litSweep(cx, cy, light, peak = strength, side = 0.35f * strength, low = 0.12f * strength)
        paint.strokeWidth = 1.2f
        canvas.drawRoundRect(RectF(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset), radius - inset, radius - inset, paint)
        // Specular glints where the light meets the corners.
        glint(canvas, rect, radius, topLeft = true, alpha = 0.95f * strength)
        glint(canvas, rect, radius, topLeft = false, alpha = 0.6f * strength)
        // A darker outer hairline keeps the pane crisp on bright wallpapers.
        paint.shader = null
        paint.color = if (palette.isDark) 0x33000000 else 0x24000000
        paint.strokeWidth = 0.6f
        canvas.drawRoundRect(RectF(rect.left + 0.3f, rect.top + 0.3f, rect.right - 0.3f, rect.bottom - 0.3f), radius, radius, paint)
        paint.style = Paint.Style.FILL
    }

    /**
     * Light around the pane, clockwise from 3 o'clock: [peak] at the top-left (225°) and a little
     * less at the bottom-right (45°), [low] where the edge turns away (135°, 315°), [side] between.
     */
    private fun litSweep(cx: Float, cy: Float, color: Argb, peak: Float, side: Float, low: Float) = SweepGradient(
        cx, cy,
        intArrayOf(
            color.withAlpha(side).value,
            color.withAlpha(peak * 0.8f).value,
            color.withAlpha(side).value,
            color.withAlpha(low).value,
            color.withAlpha(side).value,
            color.withAlpha(peak).value,
            color.withAlpha(side).value,
            color.withAlpha(low).value,
            color.withAlpha(side).value,
        ),
        floatArrayOf(0f, 0.125f, 0.25f, 0.375f, 0.5f, 0.625f, 0.75f, 0.875f, 1f),
    )

    private fun glint(canvas: Canvas, rect: RectF, radius: Float, topLeft: Boolean, alpha: Float) {
        val r = max(radius, 8f)
        val inset = 1.4f
        val oval = if (topLeft) {
            RectF(rect.left + inset, rect.top + inset, rect.left + r * 2 - inset, rect.top + r * 2 - inset)
        } else {
            RectF(rect.right - r * 2 + inset, rect.bottom - r * 2 + inset, rect.right - inset, rect.bottom - inset)
        }
        val start = if (topLeft) 195f else 15f
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = Argb.White.withAlpha(alpha * 0.8f).value
        paint.strokeWidth = 2.4f
        paint.maskFilter = BlurMaskFilter(1.8f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawArc(oval, start, 60f, false, paint)
        paint.maskFilter = null
        paint.color = Argb.White.withAlpha(alpha).value
        paint.strokeWidth = 1f
        canvas.drawArc(oval, start + 16f, 28f, false, paint)
        paint.strokeCap = Paint.Cap.BUTT
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
        precipitation(canvas, rect, visual, alpha = 0.55f, seed = seed)
        if (visual.lightning > 0f) lightning(canvas, rect, seed)

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
        canvas.restore()
        if (config.glassRim) glassBezel(canvas, rect, radius, palette, strength = 0.85f) else rim(canvas, rect, radius, palette, strength = 0.75f)
    }

    private fun celestialBody(canvas: Canvas, rect: RectF, anchor: SkyAnchor, palette: WidgetPalette, visual: WeatherVisual) {
        val w = rect.width()
        val h = rect.height()
        val cx = w * anchor.x
        val cy = h * anchor.y
        val size = min(w, h)
        val veil = 1f - visual.cloudCover * 0.75f
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

    private fun precipitation(canvas: Canvas, rect: RectF, visual: WeatherVisual, alpha: Float, seed: Int) {
        val w = rect.width()
        val h = rect.height()
        val rnd = Random(seed * 17 + 3)
        if (visual.rain > 0.05f) {
            paint.shader = null
            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeWidth = 0.9f
            val slant = 0.18f + visual.wind * 0.35f
            val n = (w * h / 400f * visual.rain).toInt().coerceIn(6, 220)
            repeat(n) {
                val x = rnd.nextFloat() * w * 1.2f
                val y = rnd.nextFloat() * h
                val len = 5f + rnd.nextFloat() * 9f * (0.5f + visual.rain)
                paint.color = Argb.White.withAlpha(alpha * (0.35f + rnd.nextFloat() * 0.65f)).value
                canvas.drawLine(x, y, x - len * slant, y + len, paint)
            }
            paint.style = Paint.Style.FILL
        }
        if (visual.snow > 0.05f || visual.hail > 0.05f) {
            paint.shader = null
            val n = (w * h / 520f * max(visual.snow, visual.hail)).toInt().coerceIn(8, 160)
            repeat(n) {
                val x = rnd.nextFloat() * w
                val y = rnd.nextFloat() * h
                val r = if (visual.hail > visual.snow) 0.9f + rnd.nextFloat() * 0.8f else 0.7f + rnd.nextFloat() * 1.8f
                paint.color = Argb.White.withAlpha(alpha * 1.4f * (0.4f + rnd.nextFloat() * 0.6f)).value
                canvas.drawCircle(x, y, r, paint)
            }
        }
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

    private fun tonal(canvas: Canvas, rect: RectF, radius: Float, config: WidgetConfig, palette: WidgetPalette, dynamic: DynamicTones) {
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
            return SkyAnchor(x, y, isSun, elevation, moonPhase)
        }
    }
}
