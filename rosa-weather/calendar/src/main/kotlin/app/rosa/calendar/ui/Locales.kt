package app.rosa.calendar.ui

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalLocale
import java.util.Locale

/**
 * The phone's language as a composable reads it: what shows it is redrawn when it changes. A
 * language chosen for the app alone may come without a region ("ru"), and weeks would then start
 * on the wrong day: the region is then the phone's own.
 */
@Composable
@ReadOnlyComposable
internal fun currentLocale(): Locale = withRegion(LocalLocale.current.platformLocale, systemLocale())

internal fun withRegion(locale: Locale, system: Locale?): Locale =
    if (locale.country.isNotEmpty() || system == null || system.country.isEmpty()) {
        locale
    } else {
        Locale.Builder().setLocale(locale).setRegion(system.country).build()
    }

private fun systemLocale(): Locale? = runCatching { Resources.getSystem().configuration.locales[0] }.getOrNull()
