package app.rosa.weather.ui.home

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import app.rosa.weather.core.designsystem.sky.SkyStage
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HeroBodyStageTest {
    // A 411 × 914 dp phone at 2.625×, laid out like the home screen.
    private val density = Density(2.625f)
    private val page = Size(1080f, 2400f)
    private fun dp(v: Float) = v * density.density
    private val row = Rect(dp(24f), dp(130f), page.width - dp(24f), dp(254f))

    private fun discLeftEdge(stage: SkyStage) = stage.left * page.width - page.height * SkyStage.BODY_RADIUS

    @Test
    fun `sun travels beside the numerals, never behind them`() {
        val numeral = Rect(dp(24f), dp(130f), dp(180f), dp(254f)) // "17°"
        val stage = heroBodyStage(row, numeral, page, density)!!
        assertThat(discLeftEdge(stage)).isGreaterThan(numeral.right + dp(24f))
        assertThat(stage.right * page.width).isLessThan(page.width)
        // Vertically it stays within the numeral row, clear of the text below.
        assertThat(stage.top * page.height).isAtLeast(row.top)
        assertThat(stage.bottom * page.height).isAtMost(row.bottom)
        assertThat(stage.right).isGreaterThan(stage.left)
    }

    @Test
    fun `wide numerals push the path right`() {
        val narrow = heroBodyStage(row, Rect(dp(24f), dp(130f), dp(180f), dp(254f)), page, density)!!
        val wide = Rect(dp(24f), dp(130f), dp(262f), dp(254f)) // "−12°"
        val stage = heroBodyStage(row, wide, page, density)!!
        assertThat(stage.left).isGreaterThan(narrow.left)
        assertThat(discLeftEdge(stage)).isGreaterThan(wide.right + dp(24f))
    }

    @Test
    fun `huge numerals pin the body to the edge instead of overlapping`() {
        val huge = Rect(dp(24f), dp(130f), dp(380f), dp(254f))
        val stage = heroBodyStage(row, huge, page, density)!!
        assertThat(stage.left).isEqualTo(stage.right)
        assertThat(stage.right * page.width + page.height * SkyStage.BODY_RADIUS).isLessThan(page.width)
    }

    @Test
    fun `path runs east to west and rises with elevation`() {
        val stage = SkyStage.Default
        assertThat(stage.at(0f, 0f).x).isLessThan(stage.at(1f, 0f).x)
        assertThat(stage.at(0.5f, 1f).y).isLessThan(stage.at(0.5f, 0f).y)
    }

    @Test
    fun `nothing measured yet means no stage`() {
        assertThat(heroBodyStage(Rect.Zero, Rect.Zero, page, density)).isNull()
    }
}
