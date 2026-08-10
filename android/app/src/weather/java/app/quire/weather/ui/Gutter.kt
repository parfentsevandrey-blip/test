package app.quire.weather.ui

import androidx.compose.ui.unit.dp

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

/**
 * How far every card on the weather screen stands off the page.
 *
 * Flat cards are right for the calendar's year, where twelve of them are transformed at once and
 * twelve resting shadows are twelve more layers to compose. Nothing here is transformed, and the
 * page these sit on is a gradient with weather in it — without a shadow a card is a lighter patch
 * of the same page rather than an object on top of it. This is the third of the three cues that
 * say *raised*, the other two being the highlight and the shade the glass draws itself.
 */
internal val CardLift = 3.dp

/**
 * The corner every full-width card on the weather screen wears, and the outer corner of the
 * readings slab. Larger than Material's card default, which is the Expressive direction — a page
 * whose objects are generously rounded reads as designed rather than defaulted — and one value
 * shared, so the slab's outer corners and the cards below it are visibly the same family.
 */
internal val CardCorner = 20.dp
