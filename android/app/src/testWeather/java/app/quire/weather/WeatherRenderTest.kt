package app.quire.weather

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.content.ContextCompat
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import app.quire.calendar.core.Accent
import app.quire.calendar.core.Prefs
import app.quire.calendar.core.Skin
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The weather icons, drawn.
 *
 * A weather icon is only worth anything if it can be told apart from the other eleven at the size
 * a widget shows it, and that is not something a compiler or an assertion can judge. So all twelve
 * are drawn onto one sheet, at the size they are actually used, and the sheet is looked at.
 *
 * The assertions here only catch the mechanical failures — a path that parses to nothing, an icon
 * that came out identical to another — which are the ones easy to miss by eye.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class WeatherRenderTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val outputDir = File("build/screenshots").apply { mkdirs() }

    private fun draw(id: Int, size: Int, tint: Int): Bitmap {
        val icon = ContextCompat.getDrawable(context, id)!!.mutate()
        icon.setTint(tint)
        icon.setBounds(0, 0, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        icon.draw(Canvas(bitmap))
        return bitmap
    }

    /** How much of the tile the glyph actually covers, as a fraction of its pixels. */
    private fun ink(bitmap: Bitmap): Float {
        var lit = 0
        for (x in 0 until bitmap.width) {
            for (y in 0 until bitmap.height) {
                if (android.graphics.Color.alpha(bitmap.getPixel(x, y)) > 24) lit++
            }
        }
        return lit.toFloat() / (bitmap.width * bitmap.height)
    }

    /** A coarse signature of where the ink is, for telling two glyphs apart. */
    private fun signature(bitmap: Bitmap): List<Int> {
        val cells = MutableList(16) { 0 }
        for (x in 0 until bitmap.width) {
            for (y in 0 until bitmap.height) {
                if (android.graphics.Color.alpha(bitmap.getPixel(x, y)) > 24) {
                    val cell = (y * 4 / bitmap.height) * 4 + (x * 4 / bitmap.width)
                    cells[cell] = cells[cell] + 1
                }
            }
        }
        return cells.map { it * 100 / (bitmap.width * bitmap.height / 16) }
    }

    @Test
    fun `every sky has a picture, and no two are the same`() {
        val size = 96
        // By drawable rather than by sky: several states share a picture on purpose — mostly
        // clear and partly cloudy are one thing to look at — so what must be distinct is the set
        // of pictures, not the set of states.
        val pairs = Sky.entries
            .flatMap { listOf(it.name to it.dayIcon, "${it.name}·night" to it.nightIcon) }
            .distinctBy { it.second }

        // One sheet, dark ground, at the size the widget draws them.
        val columns = 6
        val rows = (pairs.size + columns - 1) / columns
        val sheet = Bitmap.createBitmap(columns * size, rows * size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(0xFF2A1A18.toInt())

        val signatures = HashMap<String, List<Int>>()
        pairs.forEachIndexed { index, (name, id) ->
            val glyph = draw(id, size, 0xFFF3E4E0.toInt())
            canvas.drawBitmap(glyph, (index % columns) * size.toFloat(), (index / columns) * size.toFloat(), null)

            val covered = ink(glyph)
            assertTrue("$name drew nothing", covered > 0.02f)
            assertTrue("$name drew almost the whole tile ($covered)", covered < 0.75f)
            signatures[name] = signature(glyph)
        }

        File(outputDir, "weather-icons.png").outputStream().use {
            sheet.compress(Bitmap.CompressFormat.PNG, 100, it)
        }

        val names = signatures.keys.toList()
        for (i in names.indices) {
            for (j in i + 1 until names.size) {
                assertTrue(
                    "${names[i]} and ${names[j]} draw the same picture",
                    signatures[names[i]] != signatures[names[j]],
                )
            }
        }
    }

    /**
     * The card on a phone with the type turned up, in the language whose word for "today" is
     * seven characters long.
     *
     * Both faults this catches came back from a real home screen. The chance of rain was sliced
     * off the bottom, because the type was asked for in sp and budgeted for in dp — a widget is a
     * fixed rectangle that cannot scroll or grow, so above a font scale of one the arithmetic was
     * simply wrong. And "Сегодня" came out as "Сег…", because whether the word fits was never
     * asked.
     *
     * Checked from the layout rather than from the picture: a view laid out past the bottom of
     * the one holding it is exactly what clipping is, and it needs no coordinates to look for.
     */
    @Test
    fun `the card fits its box with the type turned up`() {
        WeatherStore.save(context, stub())
        val original = java.util.Locale.getDefault()
        val configuration = context.resources.configuration
        val metrics = context.resources.displayMetrics
        val wasScale = configuration.fontScale
        @Suppress("DEPRECATION")
        val wasScaled = metrics.scaledDensity
        try {
            // On the configuration as well as on the JVM: the day names come from java.time and
            // read the default locale, but "Сегодня" comes from resources and reads this one.
            java.util.Locale.setDefault(java.util.Locale("ru", "RU"))
            configuration.setLocale(java.util.Locale("ru", "RU"))
            configuration.fontScale = 1.3f
            // Both, because they are two different things and only one of them is what a text
            // size in sp is actually multiplied by: `Configuration.fontScale` is what the
            // renderer reads to do its arithmetic, and `scaledDensity` is what TypedValue reads
            // when the size is applied. Setting the first alone makes a test that agrees with
            // the code about the wrong answer.
            @Suppress("DEPRECATION")
            metrics.scaledDensity = metrics.density * 1.3f
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(configuration, metrics)

            listOf(190 to 170, 340 to 160, 340 to 230).forEach { (widthDp, heightDp) ->
                val widgetId = 70 + heightDp
                Prefs.get(context).widget(widgetId).apply {
                    skin = Skin.COLOUR
                    accent = Accent.PLUM
                    opacity = 100
                    dynamic = false
                }
                val host = FrameLayout(context)
                host.addView(
                    WeatherWidgetRenderer.build(context, widgetId, widthDp, heightDp)
                        .apply(context, host),
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
                render(host, widthDp, heightDp, "weather-large-type-$widthDp")

                val over = overflowing(host)
                assertTrue("${widthDp}x$heightDp clips: $over", over == null)

                val texts = ArrayList<String>()
                collectText(host, texts)
                assertTrue(
                    "${widthDp}x$heightDp truncated something: $texts",
                    texts.none { it.contains("…") },
                )
                // Either the word or the weekday, never a stump of the word.
                assertTrue(
                    "${widthDp}x$heightDp wrote a fragment of today: $texts",
                    texts.none { it.startsWith("Сег") && it != "Сегодня" },
                )
            }
        } finally {
            java.util.Locale.setDefault(original)
            configuration.setLocale(original)
            configuration.fontScale = wasScale
            @Suppress("DEPRECATION")
            metrics.scaledDensity = wasScaled
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(configuration, metrics)
        }
    }

    /**
     * The first thing laid out too small for what it holds, or null if nothing is.
     *
     * Two shapes of the same fault. A view can be placed past the edge of the one holding it,
     * which is the obvious one. Or — and this is what a widget actually does, because a
     * LinearLayout with a fixed height hands the last child whatever is left — the view fits and
     * its text does not: the row is nineteen pixels tall and the line inside it is thirty-five.
     * From outside, both are a number with its bottom sliced off.
     */
    private fun overflowing(view: android.view.View, path: String = "root"): String? {
        if (view is android.widget.TextView) {
            val room = view.height - view.paddingTop - view.paddingBottom
            val needs = view.layout?.height ?: 0
            if (needs > room + 1) {
                return "$path '${view.text}' needs ${needs}px in $room"
            }
        }
        if (view !is ViewGroup) return null
        for (index in 0 until view.childCount) {
            val child = view.getChildAt(index)
            if (child.visibility == android.view.View.GONE) continue
            val name = "$path > ${child.javaClass.simpleName}"
            val text = (child as? android.widget.TextView)?.text?.toString().orEmpty()
            if (child.bottom > view.height + 1 || child.top < -1) {
                return "$name '$text' spans ${child.top}..${child.bottom} in ${view.height}"
            }
            overflowing(child, name)?.let { return it }
        }
        return null
    }

    /**
     * The card, through `RemoteViews.apply` — the launcher's own inflater, with its `@RemoteView`
     * filter, which is the only thing that can tell you a view class would have been rejected.
     *
     * Three placements: the four-by-two it is designed for, a half-width one, and the two-by-two
     * where the strip has to give way rather than be squeezed into unreadable columns.
     */
    @Test
    fun `the card reads at every placement it allows`() {
        val forecast = stub()
        WeatherStore.save(context, forecast)

        listOf(
            Triple("weather-wide", 340 to 160, 60),
            Triple("weather-half", 175 to 160, 61),
            Triple("weather-small", 155 to 110, 62),
            // A card a row taller than the one it was designed for. The strip gets enough height
            // to buy a line back, and what it buys is the chance of rain under each day.
            Triple("weather-tall", 340 to 230, 63),
        ).forEach { (name, size, widgetId) ->
            Prefs.get(context).widget(widgetId).apply {
                skin = Skin.COLOUR
                accent = Accent.PLUM
                opacity = 100
                dynamic = false
            }
            val views = WeatherWidgetRenderer
                .build(context, widgetId, size.first, size.second)
            val host = FrameLayout(context).apply { setBackgroundColor(0xFF101014.toInt()) }
            host.addView(
                views.apply(context, host),
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            val bitmap = render(host, size.first, size.second, name)

            val texts = ArrayList<String>()
            collectText(host, texts)
            assertTrue("$name lost the temperature: $texts", texts.any { it == "12°" })
            assertTrue("$name lost the place: $texts", texts.any { it.contains("Порту") })

            val colours = HashSet<Int>()
            var x = 0
            while (x < bitmap.width) {
                var y = 0
                while (y < bitmap.height) {
                    colours += bitmap.getPixel(x, y); y += 3
                }
                x += 3
            }
            assertTrue("$name painted only ${colours.size} colours", colours.size > 8)

            // The wide and half cards have room for the forecast; the small one must have given
            // it up rather than drawn columns too narrow to read.
            // Nothing on the card may be truncated: an ellipsis in a strip column means the
            // column was drawn narrower than the number it had to hold.
            assertTrue("$name truncated something: $texts", texts.none { it.contains("…") })

            // Counted inside the strip rather than over the whole card, so the big temperature
            // at the top cannot be mistaken for a forecast day.
            val strip = ArrayList<String>()
            host.findViewById<android.view.View>(app.quire.R.id.strip)
                ?.let { collectText(it, strip) }
            val days = strip.count { it.contains("°") }
            // What each placement owes: the card it is designed for shows all five days, a
            // half-width one shows as many as fit without truncating, and the smallest gives the
            // strip up rather than drawing columns nobody can read.
            val owed = when (name) {
                "weather-wide", "weather-tall" -> 5
                "weather-half" -> 3
                else -> 0
            }
            // The rain line is bought with height and only with height. The tall card has it;
            // the one it was designed for spends the same points on an icon worth looking at.
            val wet = strip.count { it.endsWith("%") }
            if (name == "weather-tall") {
                assertTrue("the tall card did not write a chance of rain: $strip", wet >= 3)
            } else {
                assertTrue("$name found room for rain it does not have: $strip", wet == 0)
            }
            assertTrue(
                "$name showed $days days of forecast, expected at least $owed: $strip",
                days >= owed,
            )
            if (owed == 0) assertTrue("$name kept a strip it has no room for: $strip", days == 0)
        }
    }

    /**
     * The filled card in daylight.
     *
     * It used to be pinned dark whatever the phone was doing, which on a bright morning is a
     * widget that forgot to look outside. The two faces are rendered side by side so the pair can
     * be judged as a pair — they have to be recognisably the same card, not two designs.
     */
    @Test
    fun `the filled card has a daylight face`() {
        WeatherStore.save(context, stub())
        val widgetId = 70
        Prefs.get(context).widget(widgetId).apply {
            skin = Skin.COLOUR
            accent = Accent.PLUM
            opacity = 100
            dynamic = false
        }

        val shots = listOf("weather-light" to false, "weather-dark" to true).map { (name, night) ->
            org.robolectric.RuntimeEnvironment.setQualifiers(if (night) "+night" else "+notnight")
            val views = app.quire.weather.WeatherWidgetRenderer
                .build(context, widgetId, 340, 160)
            val host = FrameLayout(context).apply { setBackgroundColor(0xFF7A7A80.toInt()) }
            host.addView(
                views.apply(context, host),
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            name to render(host, 340, 160, name)
        }
        org.robolectric.RuntimeEnvironment.setQualifiers("+notnight")

        // The card is what the corner pixel is, since the surface reaches the edges.
        fun luma(bitmap: Bitmap): Int {
            val p = bitmap.getPixel(bitmap.width / 2, 6)
            return (
                android.graphics.Color.red(p) * 299 +
                    android.graphics.Color.green(p) * 587 +
                    android.graphics.Color.blue(p) * 114
                ) / 1000
        }

        val light = luma(shots[0].second)
        val dark = luma(shots[1].second)
        assertTrue("the daylight card came out dark (luma $light)", light > 170)
        assertTrue("the night card came out light (luma $dark)", dark < 90)
    }

    /**
     * One picture, both faces.
     *
     * This is the launcher's side of the theme fix. The card that sat pale on a dark home screen
     * was painted once, in whatever face the process wore at paint time, and stayed that way
     * until some watcher fired — and on the phone that mattered, none did. Every colour is now
     * applied with the paired day/night setter, so the test builds the RemoteViews ONCE under a
     * light configuration and merely re-applies the same object under a dark one: if the card
     * comes out dark anyway, the launcher can flip the theme with no help from this app at all.
     */
    @Test
    fun `one picture wears both faces`() {
        WeatherStore.save(context, stub())
        val widgetId = 71
        Prefs.get(context).widget(widgetId).apply {
            skin = Skin.COLOUR
            accent = Accent.PLUM
            opacity = 100
            dynamic = false
        }

        org.robolectric.RuntimeEnvironment.setQualifiers("+notnight")
        val views = app.quire.weather.WeatherWidgetRenderer.build(context, widgetId, 340, 160)

        fun shoot(name: String): Bitmap {
            val host = FrameLayout(context).apply { setBackgroundColor(0xFF7A7A80.toInt()) }
            host.addView(
                views.apply(context, host),
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            return render(host, 340, 160, name)
        }

        fun luma(bitmap: Bitmap): Int {
            val p = bitmap.getPixel(bitmap.width / 2, 6)
            return (
                android.graphics.Color.red(p) * 299 +
                    android.graphics.Color.green(p) * 587 +
                    android.graphics.Color.blue(p) * 114
                ) / 1000
        }

        val light = luma(shoot("widget-faces-light"))
        org.robolectric.RuntimeEnvironment.setQualifiers("+night")
        val dark = luma(shoot("widget-faces-dark"))
        org.robolectric.RuntimeEnvironment.setQualifiers("+notnight")

        assertTrue("the light application came out dark (luma XX)".replace("XX", "" + light), light > 170)
        assertTrue(
            "the picture painted in the light did not go dark when applied dark (luma XX)"
                .replace("XX", "" + dark),
            dark < 90,
        )
    }

    /** A card placed before the first fetch says so, rather than showing a plausible zero. */
    @Test
    fun `a card with nothing fetched yet says so`() {
        WeatherStore.clear(context)
        val views = WeatherWidgetRenderer.build(context, 63, 340, 160)
        val host = FrameLayout(context).apply { setBackgroundColor(0xFF101014.toInt()) }
        host.addView(
            views.apply(context, host),
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        render(host, 340, 160, "weather-empty")

        val texts = ArrayList<String>()
        collectText(host, texts)
        assertTrue("an unfetched card claimed a temperature: $texts", texts.none { it == "0°" })
        assertTrue("an unfetched card said nothing: $texts", texts.any { it.contains("…") })
    }

    private fun stub(): Forecast {
        val today = java.time.LocalDate.now()
        val skies = listOf(
            Sky.SHOWERS,
            Sky.PARTLY_CLOUDY,
            Sky.CLEAR,
            Sky.THUNDER,
            Sky.SNOW,
        )
        return Forecast(
            place = "Западный Порту",
            latitude = 55.75,
            longitude = 37.62,
            now = Conditions(
                temperature = 12.4,
                feelsLike = 10.6,
                sky = Sky.SHOWERS,
                day = false,
                humidity = 82,
                wind = 14.0,
            ),
            days = skies.mapIndexed { index, sky ->
                DayForecast(
                    date = today.plusDays(index.toLong()),
                    sky = sky,
                    high = 22.0 - index,
                    low = 11.0 - index,
                    rain = 20 * index,
                )
            },
            fetched = System.currentTimeMillis(),
        )
    }

    private fun render(
        view: android.view.View,
        widthDp: Int,
        heightDp: Int,
        name: String,
    ): Bitmap {
        val density = context.resources.displayMetrics.density
        val width = (widthDp * density).toInt()
        val height = (heightDp * density).toInt()
        view.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(height, android.view.View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        File(outputDir, "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        return bitmap
    }

    /**
     * The text a person would actually see.
     *
     * Hidden views are skipped: a card drops its detail column by setting it gone, and a walk that
     * counts gone views reports text nobody can read — which is what made this test disagree with
     * the picture beside it.
     */
    private fun collectText(view: android.view.View, out: MutableList<String>) {
        if (view.visibility != android.view.View.VISIBLE) return
        if (view is android.widget.TextView) out += view.text.toString()
        if (view is ViewGroup) for (i in 0 until view.childCount) collectText(view.getChildAt(i), out)
    }

    /** The smallest a five-day strip ever shows one: still a recognisable shape, not a blob. */
    @Test
    fun `an icon still reads at strip size`() {
        val small = draw(Sky.SHOWERS.dayIcon, 32, 0xFFFFFFFF.toInt())
        val covered = ink(small)
        assertTrue("showers vanished when shrunk ($covered)", covered > 0.05f)
        assertTrue("showers filled in when shrunk ($covered)", covered < 0.6f)
    }
}
