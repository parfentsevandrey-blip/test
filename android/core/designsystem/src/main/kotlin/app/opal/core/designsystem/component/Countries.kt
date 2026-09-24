package app.opal.core.designsystem.component

import java.util.Locale

/** Country helpers for circuit display and exit selection (no network, no external data). */
object Countries {

    /** Regional-indicator flag emoji for an ISO 3166-1 alpha-2 code. */
    fun flag(code: String): String {
        if (code.length != 2 || !code.all { it.isLetter() }) return "🏳"
        val upper = code.uppercase(Locale.ROOT)
        return String(Character.toChars(0x1F1E6 + (upper[0] - 'A'))) +
            String(Character.toChars(0x1F1E6 + (upper[1] - 'A')))
    }

    fun name(code: String, locale: Locale = Locale.getDefault()): String =
        Locale.Builder()
            .setRegion(code.uppercase(Locale.ROOT))
            .build()
            .getDisplayCountry(locale)
            .ifBlank { code.uppercase(Locale.ROOT) }

    /** Countries with many exit relays, offered first in the exit picker. */
    val popularExits =
        listOf(
            "de",
            "nl",
            "us",
            "fr",
            "se",
            "ch",
            "fi",
            "gb",
            "ca",
            "at",
            "no",
            "ro",
            "pl",
            "lu",
            "is",
        )

    val all: List<String> by lazy {
        Locale.getISOCountries().map { it.lowercase(Locale.ROOT) }.sortedBy { name(it) }
    }
}
