package app.rosa.weather.widget.layout

import app.rosa.weather.core.model.WidgetConfig
import app.rosa.weather.core.model.WidgetDensity
import app.rosa.weather.core.model.WidgetModule
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

class WidgetLayoutEngineTest {
    private val config = WidgetConfig()

    @Test
    fun `every size from 1x1 to tablet produces a valid non-overlapping layout`() {
        val configs = listOf(
            config,
            config.copy(density = WidgetDensity.Compact),
            config.copy(density = WidgetDensity.Airy, textScale = 1.3f),
            config.copy(modules = emptyList()),
            config.copy(modules = listOf(WidgetModule.Daily)),
        )
        for (cfg in configs) {
            for (w in 36..720 step 12) {
                for (h in 36..900 step 16) {
                    val layout = WidgetLayoutEngine.layout(w.toFloat(), h.toFloat(), cfg, LayoutContent(precipitationSoon = true))
                    val msg = "w=$w h=$h cfg=${cfg.density}/${cfg.modules}: ${layout.blocks}"
                    assertWithMessage(msg).that(layout.blocks.count { it is Block.Hero }).isEqualTo(1)
                    layout.blocks.forEach { b ->
                        assertWithMessage(msg).that(b.box.x).isAtLeast(-0.01f)
                        assertWithMessage(msg).that(b.box.y).isAtLeast(-0.01f)
                        assertWithMessage(msg).that(b.box.right).isAtMost(w + 0.01f)
                        assertWithMessage(msg).that(b.box.bottom).isAtMost(h + 0.01f)
                        assertWithMessage(msg).that(b.box.w).isGreaterThan(0f)
                        assertWithMessage(msg).that(b.box.h).isGreaterThan(0f)
                    }
                    for (i in layout.blocks.indices) for (j in i + 1 until layout.blocks.size) {
                        assertWithMessage(msg).that(overlaps(layout.blocks[i].box, layout.blocks[j].box)).isFalse()
                    }
                }
            }
        }
    }

    @Test
    fun `one by one shows just the essentials`() {
        val layout = WidgetLayoutEngine.layout(72f, 76f, config)
        assertThat(layout.blocks).hasSize(1)
        assertThat(layout.hero.variant).isAnyOf(HeroVariant.Compact, HeroVariant.Micro)
    }

    @Test
    fun `four by one puts the hero inline next to an hourly strip`() {
        val layout = WidgetLayoutEngine.layout(320f, 76f, config)
        assertThat(layout.hero.variant).isEqualTo(HeroVariant.Inline)
        assertThat(layout.blocks.filterIsInstance<Block.HourlyStrip>()).hasSize(1)
    }

    @Test
    fun `four by two shows hero and hours`() {
        val layout = WidgetLayoutEngine.layout(330f, 170f, config)
        assertThat(layout.blocks.any { it is Block.HourlyStrip }).isTrue()
    }

    @Test
    fun `four by four adds the daily forecast`() {
        val layout = WidgetLayoutEngine.layout(330f, 400f, config)
        assertThat(layout.hero.variant).isEqualTo(HeroVariant.Full)
        assertThat(layout.blocks.any { it is Block.HourlyStrip }).isTrue()
        assertThat(layout.blocks.any { it is Block.DailyList }).isTrue()
    }

    @Test
    fun `narrow tall column lists hours vertically`() {
        val layout = WidgetLayoutEngine.layout(80f, 330f, config)
        assertThat(layout.blocks.any { it is Block.HourlyList || it is Block.DailyList }).isTrue()
    }

    @Test
    fun `user priority decides what comes first`() {
        val daily = WidgetLayoutEngine.layout(330f, 250f, config.copy(modules = listOf(WidgetModule.Daily, WidgetModule.Hourly)))
        assertThat(daily.blocks[1]).isInstanceOf(Block.DailyList::class.java)
    }

    private fun overlaps(a: Box, b: Box) =
        a.x < b.right - 0.5f && b.x < a.right - 0.5f && a.y < b.bottom - 0.5f && b.y < a.bottom - 0.5f
}
