package app.rosa.weather.widget.motion

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.AnimatedVectorDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.RemoteViews
import androidx.core.graphics.createBitmap
import androidx.test.core.app.ApplicationProvider
import app.rosa.weather.core.model.SampleForecast
import app.rosa.weather.core.model.Units
import app.rosa.weather.core.model.WidgetConfig
import app.rosa.weather.core.model.WidgetStyle
import app.rosa.weather.core.model.momentAt
import app.rosa.weather.widget.R
import app.rosa.weather.widget.render.WidgetContent
import app.rosa.weather.widget.render.WidgetRenderRequest
import app.rosa.weather.widget.render.WidgetRenderer
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import kotlin.math.roundToInt
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The live weather tiles as the launcher plays them: every tile inflates inside the widget's
 * RemoteViews, animates only what a render thread can, loops without a jump, and — driven through
 * time here frame by frame — really rains, runs, flashes and snows. Frames land in
 * `build/live-weather/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "ru-rXX-w411dp-h891dp-xxhdpi")
class LiveWeatherTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val out = File("build/live-weather").apply { mkdirs() }

    @Test
    fun weatherPicksItsMotion() {
        fun at(scenario: SampleForecast.Scenario, now: Long, config: WidgetConfig = WidgetConfig()) =
            LiveWeather.of(config, SampleForecast.create(scenario, nowEpochSeconds = now).momentAt(now))
        assertThat(at(SampleForecast.Scenario.StormyWarm, STORM)).isEqualTo(LiveWeather.Storm)
        assertThat(at(SampleForecast.Scenario.SnowyCold, SNOW)).isAnyOf(LiveWeather.SnowLight, LiveWeather.SnowHeavy)
        assertThat(at(SampleForecast.Scenario.SunnyMild, 1_758_621_600L)).isNull()
        assertThat(at(SampleForecast.Scenario.StormyWarm, STORM, WidgetConfig(liveWeather = false))).isNull()
        assertThat(at(SampleForecast.Scenario.StormyWarm, STORM, WidgetConfig(showWeatherArt = false))).isNull()
        assertThat(at(SampleForecast.Scenario.StormyWarm, STORM, WidgetConfig(style = WidgetStyle.Paper))).isNull()
    }

    @Test
    fun tilesCoverTheWidgetWithoutRepeatingANeighbour() {
        for (weather in LiveWeather.entries) {
            val (w, h) = 314f to 342f
            val columns = weather.columns(w)
            val rows = weather.rows(h)
            assertThat(columns * weather.tileWidth).isAtLeast(w)
            assertThat(rows * weather.tileHeight).isAtLeast(h)
            if (weather.isSingleTile) continue
            for (row in 0 until rows) for (column in 0 until columns) {
                val tile = weather.tileAt(column, row)
                if (column > 0) assertThat(weather.tileAt(column - 1, row)).isNotEqualTo(tile)
                // What falls through a season's column runs on into the tile below: the same one.
                if (row > 0 && !weather.joinsDown) assertThat(weather.tileAt(column, row - 1)).isNotEqualTo(tile)
            }
        }
    }

    /** Only what AnimatedVectorDrawable can run on the render thread, and every loop closes. */
    @Test
    fun everyTileAnimatesOnTheRenderThread() {
        val groupProps = setOf("translateX", "translateY", "scaleX", "scaleY", "rotation")
        val pathProps = setOf("fillAlpha", "strokeAlpha", "trimPathStart", "trimPathEnd", "strokeWidth")
        for (id in tileDrawables()) {
            val drawable = context.getDrawable(id) as AnimatedVectorDrawable
            val animators = animators(drawable)
            assertWithMessage(context.resources.getResourceEntryName(id)).that(animators).isNotEmpty()
            for (a in animators) {
                val target = (a as ObjectAnimator).target!!
                val group = target.javaClass.simpleName == "VGroup"
                for (holder in a.values) {
                    val allowed = if (group) groupProps else pathProps
                    assertWithMessage("${holder.propertyName} on ${target.javaClass.simpleName}").that(holder.propertyName).isIn(allowed)
                }
                assertThat(a.duration).isGreaterThan(0L)
                assertThat(a.interpolator.javaClass.simpleName).isEqualTo("LinearInterpolator")
                // Ramps move a pattern by exactly one tile, spins by whole turns; everything else ends
                // where it began.
                for (holder in a.values) {
                    a.setCurrentPlayTime(0)
                    val start = a.getAnimatedValue(holder.propertyName) as Float
                    a.setCurrentFraction(0.99999f)
                    val end = a.getAnimatedValue(holder.propertyName) as Float
                    val jump = end - start
                    val tile = holder.propertyName == "translateX" && kotlin.math.abs(jump - 180f) < 0.1f ||
                        holder.propertyName == "translateY" && (kotlin.math.abs(jump - 90f) < 0.1f || kotlin.math.abs(jump - 180f) < 0.1f)
                    val turns = holder.propertyName == "rotation" && kotlin.math.abs(jump) > 1f && kotlin.math.abs(jump / 360f - kotlin.math.round(jump / 360f)) < 0.001f
                    if (!tile && !turns) assertWithMessage("${context.resources.getResourceEntryName(id)} ${holder.propertyName}").that(jump).isWithin(0.02f).of(0f)
                }
            }
        }
    }

    /** The launcher's path: RemoteViews with the tiles, applied, laid out and played through time. */
    @Test
    fun rainOnTheWidget() = film("rain", SampleForecast.Scenario.RainyAfternoon, RAIN, age = 2_400L, WidgetStyle.Glass, LiveWeather.RainHeavy)

    @Test
    fun drizzleOnTheWidget() = film("drizzle", SampleForecast.Scenario.RainyAfternoon, RAIN, age = 2_400L, WidgetStyle.Glass, LiveWeather.RainLight)

    @Test
    fun stormOnTheWidget() = film("storm", SampleForecast.Scenario.StormyWarm, STORM, age = 0L, WidgetStyle.Sky, LiveWeather.Storm)

    @Test
    fun snowOnTheWidget() = film("snow", SampleForecast.Scenario.SnowyCold, SNOW, age = 0L, WidgetStyle.Glass, LiveWeather.SnowHeavy)

    private fun film(name: String, scenario: SampleForecast.Scenario, now: Long, age: Long, style: WidgetStyle, weather: LiveWeather) {
        val (w, h) = 314f to 162f
        val density = context.resources.displayMetrics.density
        val radius = 24f
        val forecast = SampleForecast.create(scenario, nowEpochSeconds = now - age)
        val content = WidgetContent("Москва", true, forecast, now, Units())
        val config = WidgetConfig(style = style, opacity = if (style == WidgetStyle.Sky) 1f else 0.72f)
        val picture = WidgetRenderer(context).render(WidgetRenderRequest(w, h, config, content, radius, systemNight = false, seed = 5, live = true), density)
        val views = RemoteViews(context.packageName, R.layout.widget_canvas).apply {
            setImageViewBitmap(R.id.widget_image, picture)
            setLiveWeather(context.packageName, weather, w, h, radius)
        }
        val host = FrameLayout(context)
        val root = views.apply(context, host)
        host.addView(root)
        val pw = (w * density).roundToInt()
        val ph = (h * density).roundToInt()
        host.measure(View.MeasureSpec.makeMeasureSpec(pw, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(ph, View.MeasureSpec.EXACTLY))
        host.layout(0, 0, pw, ph)
        val tiles = root.findViewById<ViewGroup>(R.id.widget_motion)
        assertThat(tiles.visibility).isEqualTo(View.VISIBLE)
        assertThat(tiles.childCount).isEqualTo(weather.columns(w) * weather.rows(h))
        assertThat(tiles.getChildAt(1).left).isEqualTo((weather.tileWidth * density).toInt())

        val clip = Path().apply { addRoundRect(RectF(0f, 0f, pw.toFloat(), ph.toFloat()), radius * density, radius * density, Path.Direction.CW) }
        val frames = File(out, "$name-frames").apply { deleteRecursively(); mkdirs() }
        val fps = 25
        val seconds = if (weather == LiveWeather.Storm) 4.4f else 3.2f
        val from = if (weather == LiveWeather.Storm) 0.6f else 0.8f
        val strip = createBitmap(pw, ph * 6)
        val stripCanvas = Canvas(strip)
        val count = (seconds * fps).toInt()
        for (i in 0 until count) {
            val t = ((from + i / fps.toFloat()) * 1000).toLong()
            seek(root, t)
            val frame = createBitmap(pw, ph)
            Canvas(frame).apply {
                clipPath(clip)
                root.draw(this)
            }
            File(frames, "%03d.png".format(i)).outputStream().use { frame.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (i % (count / 6) == 0 && i / (count / 6) < 6) stripCanvas.drawBitmap(frame, 0f, (i / (count / 6)) * ph.toFloat(), null)
        }
        File(out, "$name-strip.png").outputStream().use { strip.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Every tile kind, 2 × 2, over dark and light glass, at a few moments. */
    @Test
    fun tileSheets() {
        val density = 2f
        for (weather in LiveWeather.entries) {
            val w = (2 * weather.tileWidth * density).roundToInt()
            val h = (2 * weather.tileHeight * density).roundToInt()
            val times = listOf(900L, 1_700L, 2_300L, 4_100L, 6_000L, 7_500L)
            val sheet = createBitmap(w * 2 + 12, (h + 12) * times.size)
            val canvas = Canvas(sheet)
            val frame = FrameLayout(context)
            frame.showLiveWeather(weather, 2 * weather.tileWidth, 2 * weather.tileHeight - 12f)
            frame.measure(View.MeasureSpec.makeMeasureSpec((w / density * context.resources.displayMetrics.density).roundToInt(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec((h / density * context.resources.displayMetrics.density).roundToInt(), View.MeasureSpec.EXACTLY))
            frame.layout(0, 0, frame.measuredWidth, frame.measuredHeight)
            times.forEachIndexed { row, t ->
                seek(frame, t)
                for ((column, dark) in listOf(true, false).withIndex()) {
                    canvas.save()
                    canvas.translate(column * (w + 12f), row * (h + 12f))
                    val paint = Paint().apply {
                        shader = if (dark) {
                            LinearGradient(0f, 0f, 0f, h.toFloat(), 0xFF1B2437.toInt(), 0xFF3A4660.toInt(), Shader.TileMode.CLAMP)
                        } else {
                            LinearGradient(0f, 0f, 0f, h.toFloat(), 0xFFDCE3EE.toInt(), 0xFFC5CFDE.toInt(), Shader.TileMode.CLAMP)
                        }
                    }
                    canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
                    // As the widget's own frame does: tiles past the edge are cut off.
                    canvas.clipRect(0, 0, w, h)
                    canvas.scale(density / context.resources.displayMetrics.density, density / context.resources.displayMetrics.density)
                    frame.draw(canvas)
                    canvas.restore()
                }
            }
            File(out, "tiles-${weather.name.lowercase()}.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    /** Every generated tile: the weather's and the calendar's seasons'. */
    private fun tileDrawables(): List<Int> =
        R.drawable::class.java.fields.filter { it.name.startsWith("motion_") }.map { it.getInt(null) }.also { assertThat(it.size).isAtLeast(40) }

    /** Puts every tile under [view] where its loops are [ms] after they all started together. */
    private fun seek(view: View, ms: Long) {
        if (view is ProgressBar) {
            (view.indeterminateDrawable as? AnimatedVectorDrawable)?.let { d -> animators(d).forEach { it.setCurrentPlayTime(ms % it.duration) } }
        }
        if (view is ViewGroup) for (i in 0 until view.childCount) seek(view.getChildAt(i), ms)
    }

    private val cache = HashMap<AnimatedVectorDrawable, List<ValueAnimator>>()

    /** The drawable's own animators, bound to its vector: the ones the render thread would play. */
    private fun animators(d: AnimatedVectorDrawable): List<ValueAnimator> = cache.getOrPut(d) {
        AnimatedVectorDrawable::class.java.getDeclaredMethod("ensureAnimatorSet").apply { isAccessible = true }.invoke(d)
        val set = AnimatedVectorDrawable::class.java.getDeclaredField("mAnimatorSetFromXml").apply { isAccessible = true }.get(d) as AnimatorSet
        buildList {
            fun walk(a: Animator) {
                when (a) {
                    is AnimatorSet -> a.childAnimations.forEach(::walk)
                    is ValueAnimator -> add(a)
                }
            }
            walk(set)
        }
    }

    private companion object {
        const val RAIN = 1_758_637_800L
        const val STORM = 1_758_637_800L
        const val SNOW = 1_758_610_800L
    }
}
