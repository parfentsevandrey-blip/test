package app.rosa.weather.core.designsystem.glyph

import android.graphics.Canvas
import android.graphics.RectF
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import app.rosa.weather.core.model.WeatherCondition
import kotlin.math.roundToInt

/**
 * Pictograms rendered once into bitmaps and reused everywhere the same glyph appears.
 *
 * A glass pictogram is made of soft shadows and inner light clipped to the cloud. Drawn live, the
 * GPU has to re-rasterise those masks on the CPU whenever the glyph lands on a new fraction of a
 * pixel, which is every frame of every scroll, for every glyph on screen. As a bitmap it is one
 * texture draw. Only the moving parts of animated glyphs are still drawn live (see
 * [WeatherGlyphPainter.Part]).
 *
 * Main-thread use (the Compose draw phase); access is synchronised anyway.
 */
object GlyphRaster {
    private const val PHASES = 48
    private const val CACHE_BYTES = 12 * 1024 * 1024

    private data class Key(
        val condition: WeatherCondition,
        val isDay: Boolean,
        val phase: Int,
        val tone: WeatherGlyphPainter.Tone,
        val tint: Int,
        val onLight: Boolean,
        val part: WeatherGlyphPainter.Part,
        val width: Int,
        val height: Int,
    )

    private val painter = WeatherGlyphPainter()
    private val cache = object : LruCache<Key, ImageBitmap>(CACHE_BYTES) {
        override fun sizeOf(key: Key, value: ImageBitmap) = value.asAndroidBitmap().allocationByteCount
    }

    @Synchronized
    fun get(
        condition: WeatherCondition,
        isDay: Boolean,
        moonPhase: Double,
        tone: WeatherGlyphPainter.Tone,
        tint: Int,
        onLight: Boolean,
        part: WeatherGlyphPainter.Part,
        width: Int,
        height: Int,
    ): ImageBitmap {
        // Only what changes the picture goes into the key, so glyphs are shared as widely as possible.
        val phase = if (isDay) 0 else (moonPhase.mod(1.0) * PHASES).roundToInt() % PHASES
        val mono = tone == WeatherGlyphPainter.Tone.Mono
        val key = Key(condition, isDay, phase, tone, if (mono) tint else 0, !mono && onLight, part, width, height)
        cache.get(key)?.let { return it }
        val bitmap = createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1))
        painter.draw(
            Canvas(bitmap), condition, isDay, RectF(0f, 0f, width.toFloat(), height.toFloat()),
            tone, tint, phase.toDouble() / PHASES, time = 0f, onLightBackground = onLight, part = part,
        )
        bitmap.prepareToDraw()
        return bitmap.asImageBitmap().also { cache.put(key, it) }
    }
}
