package app.quire.calendar.m3

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.intl.Locale as ComposeLocale
import java.util.Locale

/**
 * The locale, read the way composition can observe.
 *
 * `Locale.getDefault()` is a plain global: a composable that reads it keeps whatever it saw the
 * first time, so switching the phone's language leaves month names, weekday initials and date
 * formats in the old one until the process restarts. Compose publishes the locale as state, and
 * going through that is what makes a language change repaint the calendar.
 */
@Composable
internal fun rememberLocale(): Locale {
    val tag = ComposeLocale.current.toLanguageTag()
    return remember(tag) { Locale.forLanguageTag(tag) }
}
