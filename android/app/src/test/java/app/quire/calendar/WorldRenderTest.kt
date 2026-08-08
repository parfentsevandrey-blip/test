package app.quire.calendar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import androidx.test.core.app.ApplicationProvider
import app.quire.calendar.core.AgendaEntry
import app.quire.calendar.core.CalendarSource
import app.quire.calendar.core.DayLoad
import app.quire.calendar.core.MonthModel
import app.quire.calendar.world.DayPanel
import app.quire.calendar.world.Hud
import app.quire.calendar.world.MonthPlate
import app.quire.calendar.world.SearchPanel
import app.quire.calendar.world.SettingsPanel
import app.quire.calendar.world.WorldView
import app.quire.engine.anim.MotionProfile
import app.quire.engine.design.Metrics
import app.quire.engine.design.Theme
import app.quire.engine.scene.Camera3D
import app.quire.engine.scene.Quad3D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * The three drawn surfaces of the new world, put through a real Canvas and written out as PNGs.
 *
 * The point is the same as the older render test: a Kotlin compiler cannot tell whether a Path
 * was ever given a point, whether a projected quad landed inside the viewport, or whether a
 * bitmap cache hands back a recycled bitmap. Only drawing it can.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class WorldRenderTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val outputDir = File("build/screenshots").apply { mkdirs() }

    private val density: Float get() = context.resources.displayMetrics.density
    private val today: LocalDate = LocalDate.of(2026, 8, 8)
    private val month: YearMonth = YearMonth.from(today)

    private fun theme(dark: Boolean) = Theme(0xFFC0402B.toInt(), dark)

    private fun metrics() = Metrics(density)

    private fun surface(widthDp: Int, heightDp: Int): Bitmap = Bitmap.createBitmap(
        (widthDp * density).toInt(),
        (heightDp * density).toInt(),
        Bitmap.Config.ARGB_8888,
    )

    private fun write(bitmap: Bitmap, name: String) {
        File(outputDir, "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    /** Channel-sum difference between two colours, for telling ink from the plane behind it. */
    private fun distance(a: Int, b: Int): Int =
        Math.abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)) +
            Math.abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)) +
            Math.abs((a and 0xFF) - (b and 0xFF))

    /** How many distinct colours the picture holds, sampled — one means nothing was drawn. */
    private fun distinctColours(bitmap: Bitmap): Int {
        val colours = HashSet<Int>()
        var x = 0
        while (x < bitmap.width) {
            var y = 0
            while (y < bitmap.height) {
                colours += bitmap.getPixel(x, y)
                y += 3
            }
            x += 3
        }
        return colours.size
    }

    private fun loads(): Map<LocalDate, DayLoad> {
        val out = HashMap<LocalDate, DayLoad>()
        val first = MonthModel.cells(month, DayOfWeek.MONDAY).first()
        var i = 0
        while (i < MonthModel.CELLS) {
            val date = first.plusDays(i.toLong())
            // A spread of loads so the density tint has something to grade, and some days with
            // more marks than can be drawn, to exercise the cap.
            val count = (i * 7) % 5
            if (count > 0) {
                out[date] = DayLoad(
                    count,
                    IntArray(count) { slot -> 0xFF000000.toInt() or (0x2266AA + slot * 0x114411) },
                )
            }
            i++
        }
        return out
    }

    private fun entries(): List<AgendaEntry> {
        val base = today.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        return listOf(
            AgendaEntry(1, base, base, true, "All-day offsite", "Studio", 0xFF2E4A7D.toInt(), "Work"),
            AgendaEntry(
                2,
                base + 9 * 3_600_000L,
                base + 10 * 3_600_000L,
                false,
                "Standup",
                null,
                0xFF4C5D3C.toInt(),
                "Work",
            ),
            AgendaEntry(
                3,
                base + 13 * 3_600_000L,
                base + 14_400_000L + 13 * 3_600_000L,
                false,
                "A title long enough that it has to be cut off somewhere sensible",
                "Room 4, second floor",
                0,
                "Personal",
            ),
        )
    }

    /** A quad standing square to the camera and filling the viewport, as level 1 does. */
    private fun facingQuad(width: Int, height: Int): Quad3D {
        val camera = Camera3D()
        camera.update(width.toFloat(), height.toFloat())
        val quad = Quad3D()
        quad.width = 2.6f
        quad.height = 3.25f
        quad.position.set(0f, 0f, 0f)
        quad.project(camera)
        return quad
    }

    @Test
    fun `month plate paints in both skins`() {
        for (dark in listOf(false, true)) {
            val bitmap = surface(411, 640)
            val canvas = Canvas(bitmap)
            val theme = theme(dark)
            canvas.drawColor(theme.canvas)

            val plate = MonthPlate(context)
            plate.configure(theme, metrics(), DayOfWeek.MONDAY, today, true, true)
            plate.draw(canvas, facingQuad(bitmap.width, bitmap.height), month, today, loads(), 1f)

            val name = if (dark) "world-plate-ink" else "world-plate-paper"
            write(bitmap, name)
            assertTrue("$name drew nothing", distinctColours(bitmap) > 12)
            plate.release()
        }
    }

    @Test
    fun `today is marked once, not twice`() {
        val theme = theme(false)
        val quadWidth = 411
        val quadHeight = 640

        fun paint(selected: LocalDate?): Bitmap {
            val bitmap = surface(quadWidth, quadHeight)
            val canvas = Canvas(bitmap)
            canvas.drawColor(theme.canvas)
            val plate = MonthPlate(context)
            plate.configure(theme, metrics(), DayOfWeek.MONDAY, today, false, false)
            plate.draw(
                canvas,
                facingQuad(bitmap.width, bitmap.height),
                month,
                selected,
                emptyMap(),
                1f,
            )
            plate.release()
            return bitmap
        }

        // Selecting today must look exactly like selecting nothing: the disc already says so, and
        // a ring around it in the same accent marks the one day twice.
        val none = paint(null)
        val onToday = paint(today)
        val elsewhere = paint(today.plusDays(3))
        write(elsewhere, "world-plate-selected")

        assertEquals(
            "selecting today drew a second marker",
            distinctColours(none),
            distinctColours(onToday),
        )
        assertTrue(
            "selecting another day drew no marker",
            distinctColours(elsewhere) != distinctColours(none),
        )
    }

    @Test
    fun `a long title is cut rather than run off the edge`() {
        val panel = DayPanel(context)
        panel.configure(theme(false), metrics())
        val width = 411 * density
        val height = 891 * density
        panel.setBounds(RectF(0f, 0f, width, height))
        panel.setOrigin(RectF(0f, 0f, width, height))
        panel.show(today, entries(), MotionProfile.STANDARD)
        var step = 0
        while (step < 120) {
            panel.advance(1f / 60f)
            step++
        }

        val bitmap = surface(411, 891)
        val canvas = Canvas(bitmap)
        canvas.drawColor(theme(false).canvas)
        panel.draw(canvas, 1f)
        write(bitmap, "world-day-cut")

        // Measured against the panel's own fill rather than a theme colour, because everything
        // here is anti-aliased and almost nothing lands on an exact palette value. The last few
        // columns are the panel's edge stroke, which runs the full height and is not text.
        val fill = bitmap.getPixel(bitmap.width / 2, (bitmap.height * 0.8f).toInt())
        val limit = bitmap.width - 6
        var rightmost = 0
        var y = (bitmap.height * 0.22f).toInt()
        val bottom = (bitmap.height * 0.30f).toInt()
        while (y < bottom) {
            var x = limit - 1
            while (x > 0) {
                if (distance(bitmap.getPixel(x, y), fill) > 40) {
                    if (x > rightmost) rightmost = x
                    break
                }
                x--
            }
            y++
        }
        assertTrue(
            "the long title reached x=$rightmost of ${bitmap.width} instead of being cut",
            rightmost < bitmap.width * 0.96f,
        )
        assertTrue("nothing was drawn in the title band at all", rightmost > bitmap.width / 3)
    }

    @Test
    fun `month plate fades out rather than snapping`() {
        val bitmap = surface(411, 640)
        val canvas = Canvas(bitmap)
        val theme = theme(false)
        canvas.drawColor(theme.canvas)
        val plate = MonthPlate(context)
        plate.configure(theme, metrics(), DayOfWeek.MONDAY, today, true, false)
        val quad = facingQuad(bitmap.width, bitmap.height)

        plate.draw(canvas, quad, month, today, loads(), 0.35f)
        write(bitmap, "world-plate-fading")
        val faded = distinctColours(bitmap)

        canvas.drawColor(theme.canvas)
        plate.draw(canvas, quad, month, today, loads(), 1f)
        val full = distinctColours(bitmap)

        // Not an equality check on pixels: the point is only that alpha reaches the picture at
        // all, which a plate that ignored it would fail.
        assertTrue("alpha did not reach the plate", faded != full)
        plate.release()
    }

    @Test
    fun `every cell of the plate maps back to itself`() {
        val plate = MonthPlate(context)
        plate.configure(theme(false), metrics(), DayOfWeek.MONDAY, today, true, false)
        val cell = RectF()
        var index = 0
        while (index < MonthModel.CELLS) {
            plate.cellBounds(index, cell)
            assertEquals(
                "cell $index did not survive the round trip",
                index,
                plate.cellAt(cell.centerX(), cell.centerY()),
            )
            index++
        }
        // The title band and the margins are not days and must say so rather than clamping.
        assertEquals(-1, plate.cellAt(0.5f, 0.01f))
        plate.release()
    }

    @Test
    fun `plate survives being released twice`() {
        val plate = MonthPlate(context)
        plate.configure(theme(false), metrics(), DayOfWeek.MONDAY, today, true, false)
        val bitmap = surface(200, 260)
        plate.draw(Canvas(bitmap), facingQuad(bitmap.width, bitmap.height), month, null, loads(), 1f)
        plate.release()
        plate.release()
        // Drawing after a release has to rebuild rather than reach for a recycled bitmap.
        plate.draw(Canvas(bitmap), facingQuad(bitmap.width, bitmap.height), month, null, loads(), 1f)
    }

    @Test
    fun `day panel opens from a tile and lists the day`() {
        for (openness in listOf(0.35f, 1f)) {
            val bitmap = surface(411, 891)
            val canvas = Canvas(bitmap)
            val theme = theme(false)
            canvas.drawColor(theme.canvas)

            val panel = DayPanel(context)
            panel.configure(theme, metrics())
            panel.setBounds(RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat()))
            panel.setOrigin(
                RectF(
                    bitmap.width * 0.42f,
                    bitmap.height * 0.38f,
                    bitmap.width * 0.56f,
                    bitmap.height * 0.46f,
                ),
            )
            panel.show(today, entries(), MotionProfile.STANDARD)
            // Run the stagger forward so the entries are actually on screen when it is drawn.
            var step = 0
            while (step < 90) {
                panel.advance(1f / 60f)
                step++
            }
            panel.draw(canvas, openness)

            val name = if (openness < 1f) "world-day-opening" else "world-day-open"
            write(bitmap, name)
            assertTrue("$name drew nothing", distinctColours(bitmap) > 12)
        }
    }

    @Test
    fun `day panel finds the entry under a point and nothing outside itself`() {
        val panel = DayPanel(context)
        panel.configure(theme(false), metrics())
        val width = 411 * density
        val height = 891 * density
        panel.setBounds(RectF(0f, 0f, width, height))
        panel.setOrigin(RectF(0f, 0f, width, height))
        panel.show(today, entries(), MotionProfile.STANDARD)
        var step = 0
        while (step < 120) {
            panel.advance(1f / 60f)
            step++
        }
        assertNull("a point off the panel found an entry", panel.entryAt(-40f, -40f))
        assertTrue("the panel opened already scrolled", panel.scrollAtTop)
    }

    @Test
    fun `empty day still draws`() {
        val bitmap = surface(411, 891)
        val canvas = Canvas(bitmap)
        val theme = theme(true)
        canvas.drawColor(theme.canvas)
        val panel = DayPanel(context)
        panel.configure(theme, metrics())
        panel.setBounds(RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat()))
        panel.setOrigin(RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat()))
        panel.show(today, emptyList(), MotionProfile.STANDARD)
        var step = 0
        while (step < 60) {
            panel.advance(1f / 60f)
            step++
        }
        panel.draw(canvas, 1f)
        write(bitmap, "world-day-empty")
        assertTrue("the empty day said nothing", distinctColours(bitmap) > 3)
    }

    @Test
    fun `hud paints its five targets and finds each one`() {
        for (dark in listOf(false, true)) {
            val bitmap = surface(411, 891)
            val canvas = Canvas(bitmap)
            val theme = theme(dark)
            canvas.drawColor(theme.canvas)

            val hud = Hud(context)
            hud.configure(theme, metrics(), MotionProfile.STANDARD)
            hud.setSafeInsets(48f * density, 24f * density)
            hud.layout(bitmap.width.toFloat(), bitmap.height.toFloat())
            hud.draw(canvas, "August", "2026", 1, 1f)

            val name = if (dark) "world-hud-ink" else "world-hud-paper"
            write(bitmap, name)
            assertTrue("$name drew nothing", distinctColours(bitmap) > 6)

            // Five equal targets across the bar: sampling the middle of each fifth has to find
            // every action exactly once, which a bar laid out from the wrong width would fail.
            val y = bitmap.height - hud.barHeight * 0.5f
            val found = HashSet<Int>()
            var slot = 0
            while (slot < 5) {
                val x = bitmap.width * (slot + 0.5f) / 5f
                val action = hud.actionAt(x, y)
                assertTrue("no action under slot $slot", action in 1..5)
                found += action
                slot++
            }
            assertEquals("the five targets are not five distinct actions", 5, found.size)
            assertEquals("a point above the bar hit it anyway", 0, hud.actionAt(10f, 10f))
        }
    }

    @Test
    fun `a pressed hud target actually moves`() {
        val hud = Hud(context)
        hud.configure(theme(false), metrics(), MotionProfile.STANDARD)
        hud.setSafeInsets(0f, 0f)
        hud.layout(411 * density, 891 * density)

        val before = surface(411, 891)
        hud.draw(Canvas(before), "August", "2026", 1, 1f)
        val still = distinctColours(before)

        hud.press(1)
        // One frame is enough for a press to have started; if nothing moves, the profile is
        // being ignored or the spring is never advanced.
        var moved = false
        var step = 0
        while (step < 6) {
            moved = hud.advance(1f / 60f) || moved
            step++
        }
        assertTrue("the hud never moved after a press", moved)

        val after = surface(411, 891)
        hud.draw(Canvas(after), "August", "2026", 1, 1f)
        write(after, "world-hud-pressed")
        assertTrue("the pressed hud drew nothing", distinctColours(after) > 3)
        assertTrue("the still hud drew nothing", still > 3)
    }

    @Test
    fun `hud fades out with its alpha`() {
        val hidden = surface(411, 891)
        val canvas = Canvas(hidden)
        val theme = theme(false)
        canvas.drawColor(theme.canvas)
        val hud = Hud(context)
        hud.configure(theme, metrics(), MotionProfile.STANDARD)
        hud.setSafeInsets(0f, 0f)
        hud.layout(hidden.width.toFloat(), hidden.height.toFloat())
        hud.draw(canvas, "August", "2026", 1, 0f)
        assertEquals("a hud at zero alpha still drew", 1, distinctColours(hidden))
    }

    // ---- the world itself ----------------------------------------------

    private fun worldData() = object : WorldView.Data {
        override fun loads(month: YearMonth): Map<LocalDate, DayLoad> =
            if (month == this@WorldRenderTest.month) loads() else emptyMap()

        override fun agenda(date: LocalDate): List<AgendaEntry> = entries()
    }

    private fun world(dark: Boolean): WorldView {
        val view = WorldView(context)
        view.theme = theme(dark)
        view.metrics = metrics()
        // Off, so every spring lands on its target within a frame and the render is the settled
        // picture rather than whatever the motion happened to be passing through.
        view.motion = MotionProfile.OFF
        view.haptics = false
        view.depth = false
        view.density = true
        view.colouredMarks = true
        view.firstDayOfWeek = DayOfWeek.MONDAY
        view.today = today
        view.data = worldData()
        view.setSafeInsets(48f * density, 24f * density)
        return view
    }

    /**
     * Puts the view in a real window before drawing it.
     *
     * This is not ceremony. The world drives itself from `Clock`, which it only subscribes to in
     * `onAttachedToWindow` — a view that is merely measured and laid out never receives a frame,
     * so every spring stays at the value it was seeded with and an opened day renders as an empty
     * card. Hosting it and letting the looper run is what makes the render the settled picture.
     */
    private fun paint(view: WorldView, name: String): Bitmap {
        val controller = org.robolectric.Robolectric
            .buildActivity(android.app.Activity::class.java)
            .setup()
        try {
            controller.get().setContentView(
                view,
                android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            val looper = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
            var frame = 0
            while (frame < 120) {
                looper.idleFor(16, java.util.concurrent.TimeUnit.MILLISECONDS)
                frame++
            }
            val width = view.width.takeIf { it > 0 } ?: (411 * density).toInt()
            val height = view.height.takeIf { it > 0 } ?: (891 * density).toInt()
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            write(bitmap, name)
            return bitmap
        } finally {
            controller.close()
        }
    }

    @Test
    fun `the world paints at every level`() {
        for (level in 0..2) {
            val view = world(dark = false)
            view.goTo(today, level = level, animate = false)
            val bitmap = paint(view, "world-level-$level")
            assertTrue("level $level drew nothing", distinctColours(bitmap) > 12)
            assertEquals("level $level did not settle where it was sent", level, view.level)
        }
    }

    @Test
    fun `the world paints in ink`() {
        val view = world(dark = true)
        view.goTo(today, level = 1, animate = false)
        val bitmap = paint(view, "world-level-1-ink")
        assertTrue("the dark world drew nothing", distinctColours(bitmap) > 12)
    }

    @Test
    fun `stepping out of a day lands on the month and then the year`() {
        val view = world(dark = false)
        view.goTo(today, level = 2, animate = false)
        assertEquals(2, view.level)
        assertTrue("nothing to step out of at the day", view.zoomOut())
        assertEquals("stepping out of the day did not land on the month", 1, view.level)
        assertTrue("nothing to step out of at the month", view.zoomOut())
        assertEquals("stepping out of the month did not land on the year", 0, view.level)
        // The year is the outermost level: back has to leave the app rather than loop.
        assertTrue("the year claimed a step it does not have", !view.zoomOut())
    }

    @Test
    fun `the world survives being drawn before it is told anything`() {
        // A view with no theme, no data and no size must not throw on its first frame; the
        // Activity sets those in onCreate, but the first measure can arrive before they land.
        val bare = WorldView(context)
        paint(bare, "world-bare")
    }

    // ---- the two sheets ------------------------------------------------

    private fun state(seed: Int = 0xFFC0402B.toInt()) = SettingsPanel.State(
        seed = seed,
        dark = null,
        contrast = 0.2f,
        scale = 1f,
        firstDay = DayOfWeek.MONDAY,
        motion = MotionProfile.STANDARD,
        haptics = true,
        depth = true,
        density = false,
        colouredMarks = true,
        adjacent = true,
        hidden = emptySet(),
    )

    private fun calendars() = listOf(
        CalendarSource(1L, "Work", "me@example.com", 0xFF2E4A7D.toInt()),
        CalendarSource(2L, "Personal", "me@example.com", 0xFF4C5D3C.toInt()),
    )

    private fun settledSettings(theme: Theme, seed: Int = 0xFFC0402B.toInt()): SettingsPanel {
        val panel = SettingsPanel(context)
        panel.configure(theme, metrics(), MotionProfile.STANDARD)
        panel.setBounds(RectF(0f, 0f, 411 * density, 891 * density))
        panel.setCalendars(calendars())
        panel.setVersion("3.0")
        panel.show(state(seed))
        var step = 0
        while (step < 200) {
            panel.advance(1f / 60f)
            step++
        }
        return panel
    }

    @Test
    fun `settings sheet paints in both skins`() {
        for (dark in listOf(false, true)) {
            val theme = theme(dark)
            val bitmap = surface(411, 891)
            val canvas = Canvas(bitmap)
            canvas.drawColor(theme.canvas)
            val panel = settledSettings(theme)
            panel.draw(canvas, 1f)

            val name = if (dark) "world-settings-ink" else "world-settings-paper"
            write(bitmap, name)
            assertTrue("$name drew nothing", distinctColours(bitmap) > 12)
            assertTrue("the sheet did not consider itself shown", panel.visible)
        }
    }

    @Test
    fun `settings sheet is off the screen at zero and comes up on its own`() {
        val theme = theme(false)
        val bitmap = surface(411, 891)
        val canvas = Canvas(bitmap)
        canvas.drawColor(theme.canvas)
        val panel = settledSettings(theme)
        panel.draw(canvas, 0f)
        assertEquals("a sheet at zero openness still drew", 1, distinctColours(bitmap))

        canvas.drawColor(theme.canvas)
        panel.draw(canvas, 0.5f)
        write(bitmap, "world-settings-rising")
        assertTrue("a half-open sheet drew nothing", distinctColours(bitmap) > 6)
    }

    @Test
    fun `the settings preview answers the seed it is shown`() {
        val theme = theme(false)
        val cinnabar = surface(411, 891)
        Canvas(cinnabar).also { it.drawColor(theme.canvas) }
            .let { settledSettings(theme, 0xFFC0402B.toInt()).draw(it, 1f) }

        val indigo = surface(411, 891)
        Canvas(indigo).also { it.drawColor(theme.canvas) }
            .let { settledSettings(theme, 0xFF2E4A7D.toInt()).draw(it, 1f) }

        // The point of a setting called Seed is that you can see it, so the two sheets must not
        // be the same picture.
        var different = 0
        var x = 0
        while (x < cinnabar.width) {
            var y = 0
            while (y < cinnabar.height) {
                if (cinnabar.getPixel(x, y) != indigo.getPixel(x, y)) different++
                y += 7
            }
            x += 7
        }
        assertTrue("changing the seed changed nothing on screen", different > 200)
    }

    @Test
    fun `the settings sheet can be put away without a back button`() {
        // It opens full height, so there is no world left showing to tap on. That makes the grab
        // handle the only way out that does not need a system button, and it has to work.
        val panel = settledSettings(theme(false))
        var dismissed = 0
        panel.onDismiss = { dismissed++ }

        assertTrue("a tap on a row was ignored", panel.onTap(200 * density, 700 * density))
        assertEquals("tapping a row put the sheet away", 0, dismissed)

        assertTrue("the grab handle ignored a tap", panel.onTap(205 * density, 4f))
        assertEquals("the grab handle did not dismiss", 1, dismissed)

        panel.hide()
        assertTrue("the sheet still called itself visible after hide", !panel.visible)
    }

    @Test
    fun `search sheet paints its field and results`() {
        for (dark in listOf(false, true)) {
            val theme = theme(dark)
            val bitmap = surface(411, 891)
            val canvas = Canvas(bitmap)
            canvas.drawColor(theme.canvas)

            val panel = SearchPanel(context)
            panel.configure(theme, metrics(), MotionProfile.STANDARD)
            panel.setBounds(RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat()))
            panel.show()
            panel.insert("stand")
            panel.setResults("stand", entries())
            var step = 0
            while (step < 200) {
                panel.advance(1f / 60f)
                step++
            }
            panel.draw(canvas, 1f)

            val name = if (dark) "world-search-ink" else "world-search-paper"
            write(bitmap, name)
            assertTrue("$name drew nothing", distinctColours(bitmap) > 12)
            assertEquals("the field lost what was typed into it", "stand", panel.query)
        }
    }

    @Test
    fun `typing into the search field moves the caret and can be undone`() {
        val panel = SearchPanel(context)
        panel.configure(theme(false), metrics(), MotionProfile.STANDARD)
        panel.setBounds(RectF(0f, 0f, 411 * density, 891 * density))
        panel.show()
        panel.insert("meeting")
        assertEquals("meeting", panel.query)
        assertEquals("the caret did not follow the text", 7, panel.caret)
        panel.backspace()
        assertEquals("meetin", panel.query)
        assertEquals(6, panel.caret)
        panel.moveCaret(2)
        panel.insert("X")
        assertEquals("meXetin", panel.query)
        panel.clear()
        assertEquals("", panel.query)
        assertEquals(0, panel.caret)
    }

    @Test
    fun `search says something when it has nothing to show`() {
        val theme = theme(false)
        val bitmap = surface(411, 891)
        val canvas = Canvas(bitmap)
        canvas.drawColor(theme.canvas)
        val panel = SearchPanel(context)
        panel.configure(theme, metrics(), MotionProfile.STANDARD)
        panel.setBounds(RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat()))
        panel.show()
        panel.insert("zzzz")
        panel.setResults("zzzz", emptyList())
        var step = 0
        while (step < 120) {
            panel.advance(1f / 60f)
            step++
        }
        panel.draw(canvas, 1f)
        write(bitmap, "world-search-empty")
        assertTrue("an empty search said nothing at all", distinctColours(bitmap) > 6)
    }

    @Test
    @Config(sdk = [34], qualifiers = "ru-rRU-w411dp-h891dp-xxhdpi")
    fun `the sheets speak the phone's language`() {
        val theme = theme(false)
        val bitmap = surface(411, 891)
        val canvas = Canvas(bitmap)
        canvas.drawColor(theme.canvas)
        val panel = settledSettings(theme)
        panel.draw(canvas, 1f)
        write(bitmap, "world-settings-ru")

        // Cyrillic labels are longer than their English originals almost everywhere, which is
        // exactly when a hand-drawn row runs out of width, so this render is worth looking at.
        assertTrue("the translated sheet drew nothing", distinctColours(bitmap) > 12)

        val day = surface(411, 891)
        val dayCanvas = Canvas(day)
        dayCanvas.drawColor(theme.canvas)
        val list = DayPanel(context)
        list.configure(theme, metrics())
        list.setBounds(RectF(0f, 0f, day.width.toFloat(), day.height.toFloat()))
        list.setOrigin(RectF(0f, 0f, day.width.toFloat(), day.height.toFloat()))
        list.show(today, emptyList(), MotionProfile.STANDARD)
        var step = 0
        while (step < 90) {
            list.advance(1f / 60f)
            step++
        }
        list.draw(dayCanvas, 1f)
        write(day, "world-day-ru")
        assertTrue("the translated empty day drew nothing", distinctColours(day) > 3)
    }

    @Test
    fun `motion off holds every spring still`() {
        val hud = Hud(context)
        hud.configure(theme(false), metrics(), MotionProfile.OFF)
        hud.setSafeInsets(0f, 0f)
        hud.layout(411 * density, 891 * density)
        hud.press(2)
        var step = 0
        var frames = 0
        while (step < 30) {
            if (hud.advance(1f / 60f)) frames++
            step++
        }
        // Off does not mean "no state changes", it means the state arrives at once: whatever it
        // does, it must settle almost immediately rather than running for half a second.
        assertTrue("Off ran for $frames frames", frames <= 2)
        assertNotNull(hud)
    }
}
