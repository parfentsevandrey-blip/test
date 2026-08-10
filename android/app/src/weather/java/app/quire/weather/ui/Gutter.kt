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

/** The gap between a section heading and the block it names, and above the heading itself. */
internal val HeadingTop = 24.dp
internal val HeadingBottom = 8.dp
