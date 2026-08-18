package app.quire.weather.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import app.quire.weather.Sky

/**
 * The one left edge on the weather screen.
 *
 * Every block — the hero, the section headings, and each of the four cards — starts here. It is a
 * constant rather than a number typed at each call site because the screen read as crooked for
 * exactly one reason: the headings were inset 24dp and the cards under them 16dp, so every heading
 * hung eight pixels left of the thing it named, down the whole page. A shared value cannot drift.
 */
internal val Gutter = 16.dp

/**
 * The tag every block that must start at [Gutter] carries.
 *
 * A card has no text of its own to ask where it begins, and finding its edge in the picture stopped
 * being possible once there was weather falling in front of it: the page is a dithered gradient
 * with rain on it, and at some heights the wash lifts it to within a hair of the card's own colour.
 * So the blocks say where they are instead of being measured, and the test asks all four at once.
 */
internal const val BLOCK = "weather-block"

/** The gap between a section heading and the block it names, and above the heading itself. */
internal val HeadingTop = 24.dp
internal val HeadingBottom = 8.dp

/** The colour a sky wears in the small marks: its icon in the hour strip and the day rows. */
internal fun skyInk(sky: Sky, scheme: ColorScheme): Color = when (sky) {
    Sky.CLEAR, Sky.MOSTLY_CLEAR -> scheme.primary
    Sky.DRIZZLE, Sky.RAIN, Sky.SHOWERS, Sky.SLEET, Sky.THUNDER -> scheme.tertiary
    else -> scheme.secondary
}
