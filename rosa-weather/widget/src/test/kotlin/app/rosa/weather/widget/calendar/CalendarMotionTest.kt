package app.rosa.weather.widget.calendar

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
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
import app.rosa.weather.widget.R
import app.rosa.weather.widget.motion.LiveWeather
import app.rosa.weather.widget.motion.setLiveWeather
import app.rosa.weather.widget.provider.CalendarWidgetProvider
import app.rosa.weather.widget.provider.WidgetKind
import app.rosa.weather.widget.render.WidgetContent
import app.rosa.weather.widget.render.WidgetRenderRequest
import app.rosa.weather.widget.render.WidgetRenderer
import app.rosa.weather.widget.render.calendar.CalendarView
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.roundToInt
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The season moving over the calendar as the launcher plays it: the widget's own RemoteViews
 * inflated, its tiles run frame by frame. Frames land in `build/widget-gallery/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "ru-rXX-w411dp-h891dp-xxhdpi")
class CalendarMotionTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val out = File("build/widget-gallery").apply { mkdirs() }

    @Test
    fun leavesFallInOctober() = film("calendar-october", 10, LiveWeather.LeavesRed)

    @Test
    fun petalsDriftInMay() = film("calendar-may", 5, LiveWeather.Petals)

    @Test
    fun firefliesGlowInJuly() = film("calendar-july", 7, LiveWeather.Fireflies)

    private fun film(name: String, month: Int, expected: LiveWeather) {
        val config = WidgetKind.Calendar.defaultConfig
        assertThat(LiveWeather.ofSeason(config, month)).isEqualTo(expected)
        val (w, h) = 314f to 252f
        val radius = 22f
        val density = context.resources.displayMetrics.density
        val today = LocalDate.of(2026, month, 16)
        val now = today.atTime(12, 0).toEpochSecond(ZoneOffset.ofHours(3))
        val content = WidgetContent("Москва", true, SampleForecast.create(SampleForecast.Scenario.SunnyMild, nowEpochSeconds = now), now, Units())
        val view = CalendarView(YearMonth.from(today), today, locale = Locale.forLanguageTag("ru-RU"))
        val (picture, targets) = WidgetRenderer(context).renderCalendar(
            WidgetRenderRequest(w, h, config, content, radius, systemNight = false, live = true, calendar = view),
            density,
        )
        val views = RemoteViews(context.packageName, R.layout.widget_calendar).apply {
            setImageViewBitmap(R.id.widget_image, picture)
            setLiveWeather(context.packageName, expected, w, h, radius)
            setCalendarTargets(context, ComponentName(context, CalendarWidgetProvider::class.java), 1, view, targets)
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
        assertThat(tiles.childCount).isEqualTo(LiveWeather.columns(w) * LiveWeather.rows(h))

        val clip = Path().apply { addRoundRect(RectF(0f, 0f, pw.toFloat(), ph.toFloat()), radius * density, radius * density, Path.Direction.CW) }
        val frames = File(out, "$name-frames").apply { deleteRecursively(); mkdirs() }
        val fps = 20
        val count = 60
        val strip = createBitmap(pw, ph * 4)
        val stripCanvas = Canvas(strip)
        for (i in 0 until count) {
            seek(root, (1000 + i * 1000L / fps))
            val frame = createBitmap(pw, ph)
            Canvas(frame).apply {
                clipPath(clip)
                root.draw(this)
            }
            File(frames, "%03d.png".format(i)).outputStream().use { frame.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (i % 15 == 0) stripCanvas.drawBitmap(frame, 0f, (i / 15) * ph.toFloat(), null)
        }
        File(out, "$name-strip.png").outputStream().use { strip.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun seek(view: View, ms: Long) {
        if (view is ProgressBar) {
            (view.indeterminateDrawable as? AnimatedVectorDrawable)?.let { d -> animators(d).forEach { it.setCurrentPlayTime(ms % it.duration) } }
        }
        if (view is ViewGroup) for (i in 0 until view.childCount) seek(view.getChildAt(i), ms)
    }

    private val cache = HashMap<AnimatedVectorDrawable, List<ValueAnimator>>()

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
}
