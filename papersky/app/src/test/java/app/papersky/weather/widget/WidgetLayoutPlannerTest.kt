package app.papersky.weather.widget

import app.papersky.weather.widget.layout.DailyBlock
import app.papersky.weather.widget.layout.GapBlock
import app.papersky.weather.widget.layout.HeroBlock
import app.papersky.weather.widget.layout.HourlyBlock
import app.papersky.weather.widget.layout.HourlyRowsBlock
import app.papersky.weather.widget.layout.MAX_CHILDREN
import app.papersky.weather.widget.layout.Mode
import app.papersky.weather.widget.layout.PlanInput
import app.papersky.weather.widget.layout.WidgetLayoutPlanner
import app.papersky.weather.widget.layout.WidgetPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetLayoutPlannerTest {

    private val configs = listOf(
        WidgetConfig(),
        WidgetConfig(showClock = true),
        WidgetConfig(background = WidgetBackground.Paper, density = WidgetDensity.Airy, textScale = 1.4f),
        WidgetConfig(background = WidgetBackground.Clear, density = WidgetDensity.Compact, textScale = 0.8f),
        WidgetConfig(showHourly = false, showDaily = false, showDetails = false, showWhisper = false, showLocation = false),
        WidgetConfig(showFeelsLike = true, showRefresh = true, showUpdated = true, hourStep = 3),
    )

    private fun plan(w: Float, h: Float, config: WidgetConfig = WidgetConfig(), heroText: String = "−23°", fontScale: Float = 1f) =
        WidgetLayoutPlanner.plan(PlanInput(width = w, height = h, config = config, heroText = heroText, fontScale = fontScale))

    private fun assertSane(p: WidgetPlan, label: String) {
        val eps = 0.6f
        for (c in p.columns) {
            val used = c.blocks.sumOf { it.height.toDouble() }.toFloat()
            assertTrue("$label: column overflows ($used > ${c.height})", used <= c.height + eps)
            assertTrue("$label: column outside widget", c.x >= 0 && c.x + c.width <= p.width + eps)
            c.blocks.forEach { assertTrue("$label: negative height in $it", it.height >= -eps) }
            val children = c.blocks.count { it !is GapBlock }
            assertTrue("$label: $children sections exceed Glance's child limit", children <= MAX_CHILDREN)
            c.blocks.filterIsInstance<HourlyBlock>().forEach { assertTrue("$label: ${it.count} hours", it.count in 3..MAX_CHILDREN) }
            c.blocks.filterIsInstance<HourlyRowsBlock>().forEach { assertTrue("$label: ${it.rows} rows", it.rows in 1..MAX_CHILDREN) }
            c.blocks.filterIsInstance<DailyBlock>().forEach {
                assertTrue("$label: ${it.rows} days", it.rows in 2..MAX_CHILDREN)
                assertTrue("$label: day rows overflow", it.rows * it.rowHeight <= it.height + eps)
            }
            c.blocks.filterIsInstance<HeroBlock>().forEach { assertTrue("$label: invisible hero", it.tempSize > 0f || it.infoLines.isNotEmpty()) }
        }
        p.strip?.let { s ->
            assertTrue("$label: too many chips", s.chips <= MAX_CHILDREN)
            assertTrue("$label: strip hero too small", s.tempSize >= 12f)
            val fixed = s.clockWidth + s.heroWidth + s.infoWidth
            assertTrue("$label: strip overflows ($fixed > ${p.width})", fixed <= p.width - 2 * p.pad + 24f)
        }
        p.panels.forEach { r ->
            assertTrue("$label: panel out of bounds $r", r.left >= -4 && r.top >= -4 && r.right <= p.width + 4 && r.bottom <= p.height + 4)
        }
    }

    @Test
    fun everySizeProducesASaneLayout() {
        var checked = 0
        for (config in configs) {
            var w = 40f
            while (w <= 640f) {
                var h = 40f
                while (h <= 760f) {
                    for (scale in listOf(1f, 1.3f)) {
                        assertSane(plan(w, h, config, fontScale = scale), "${w}x$h $config")
                        checked++
                    }
                    h += 23f
                }
                w += 19f
            }
        }
        assertTrue(checked > 1000)
    }

    @Test
    fun modesFollowTheGrid() {
        assertEquals(Mode.Micro, plan(64f, 88f).mode)
        assertEquals(Mode.Strip, plan(292f, 88f).mode)
        assertEquals(Mode.Tower, plan(64f, 400f).mode)
        assertEquals(Mode.Card, plan(216f, 192f).mode)
        assertEquals(Mode.Panorama, plan(368f, 192f).mode)
    }

    @Test
    fun wideStripsShowAnHourlyRibbon() {
        val strip = plan(368f, 88f).strip!!
        assertTrue("chips=${strip.chips}", strip.chips >= 3)
    }

    @Test
    fun bigWidgetsUseTheSpaceForDays() {
        val p = plan(368f, 504f)
        val days = p.columns.flatMap { it.blocks }.filterIsInstance<DailyBlock>().single()
        assertTrue("rows=${days.rows}", days.rows >= 4)
        assertTrue(p.columns.flatMap { it.blocks }.any { it is HourlyBlock })
    }

    @Test
    fun heroGrowsWithTheWidget() {
        val small = plan(140f, 192f).columns.first().blocks.filterIsInstance<HeroBlock>().first().tempSize
        val big = plan(368f, 400f).columns.first().blocks.filterIsInstance<HeroBlock>().first().tempSize
        assertTrue("small=$small big=$big", big > small)
    }

    @Test
    fun narrowTowersKeepDaysCompact() {
        val p = plan(64f, 504f)
        p.columns.flatMap { it.blocks }.filterIsInstance<DailyBlock>().forEach {
            assertTrue(it.compact)
            assertTrue(!it.showGlyph)
        }
    }

    @Test
    fun panelsOnlyOnTheSceneBackground() {
        assertTrue(plan(292f, 296f).panels.isNotEmpty())
        assertTrue(plan(292f, 296f, WidgetConfig(background = WidgetBackground.Paper)).panels.isEmpty())
    }
}
