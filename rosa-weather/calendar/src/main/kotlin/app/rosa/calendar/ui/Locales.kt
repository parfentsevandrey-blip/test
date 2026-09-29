package app.rosa.calendar.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalLocale
import java.util.Locale

/** The phone's language as a composable reads it: what shows it is redrawn when it changes. */
@Composable
@ReadOnlyComposable
internal fun currentLocale(): Locale = LocalLocale.current.platformLocale
