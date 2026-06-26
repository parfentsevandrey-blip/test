package com.monthcalendar.widget

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.glance.color.ColorProviders
import androidx.glance.color.DynamicThemeColorProviders
import androidx.glance.material3.ColorProviders as GlanceColorProviders

/**
 * Resolves the [Accent] setting to a Glance [ColorProviders].
 *
 * [Accent.DYNAMIC] returns Glance's [DynamicThemeColorProviders] singleton. On
 * API 31+ each role resolves to an `@android:color/system_*` resource that the
 * launcher re-resolves at draw time, so the widget tracks the live Material You
 * system theme with no re-render needed. On API < 31 there is no Material You:
 * the same roles fall back to Glance's static baseline palette.
 *
 * The fixed accents are intentionally static [GlanceColorProviders] built from
 * hand-tuned light/dark [androidx.compose.material3.ColorScheme]s.
 */
object AccentSchemes {

    fun providersFor(accent: Accent): ColorProviders = when (accent) {
        Accent.DYNAMIC -> DynamicThemeColorProviders
        Accent.INDIGO -> indigo
        Accent.GREEN -> green
        Accent.ROSE -> rose
        Accent.AMBER -> amber
    }

    private fun build(
        primary: Color,
        onPrimary: Color,
        primaryContainer: Color,
        onPrimaryContainer: Color,
        secondaryContainer: Color,
        onSecondaryContainer: Color,
        tertiary: Color,
        // dark variants
        dPrimary: Color,
        dOnPrimary: Color,
        dPrimaryContainer: Color,
        dOnPrimaryContainer: Color,
        dSecondaryContainer: Color,
        dOnSecondaryContainer: Color,
        dTertiary: Color,
    ): ColorProviders {
        val light = lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,
            tertiary = tertiary,
            background = Color(0xFFFCF8FB),
            onBackground = Color(0xFF1B1B1F),
            surface = Color(0xFFFCF8FB),
            onSurface = Color(0xFF1B1B1F),
            surfaceVariant = Color(0xFFE4E1EC),
            onSurfaceVariant = Color(0xFF46464F),
        )
        val dark = darkColorScheme(
            primary = dPrimary,
            onPrimary = dOnPrimary,
            primaryContainer = dPrimaryContainer,
            onPrimaryContainer = dOnPrimaryContainer,
            secondaryContainer = dSecondaryContainer,
            onSecondaryContainer = dOnSecondaryContainer,
            tertiary = dTertiary,
            background = Color(0xFF131316),
            onBackground = Color(0xFFE5E1E6),
            surface = Color(0xFF1B1B1F),
            onSurface = Color(0xFFE5E1E6),
            surfaceVariant = Color(0xFF46464F),
            onSurfaceVariant = Color(0xFFC8C5D0),
        )
        return GlanceColorProviders(light = light, dark = dark)
    }

    private val indigo = build(
        primary = Color(0xFF4A52CC), onPrimary = Color.White,
        primaryContainer = Color(0xFFE0E0FF), onPrimaryContainer = Color(0xFF000965),
        secondaryContainer = Color(0xFFE2E0F9), onSecondaryContainer = Color(0xFF191A2C),
        tertiary = Color(0xFF7D5260),
        dPrimary = Color(0xFFBEC2FF), dOnPrimary = Color(0xFF1A20A0),
        dPrimaryContainer = Color(0xFF333AB5), dOnPrimaryContainer = Color(0xFFE0E0FF),
        dSecondaryContainer = Color(0xFF454559), dOnSecondaryContainer = Color(0xFFE2E0F9),
        dTertiary = Color(0xFFEFB8C8),
    )

    private val green = build(
        primary = Color(0xFF2E6B4F), onPrimary = Color.White,
        primaryContainer = Color(0xFFB2F1C9), onPrimaryContainer = Color(0xFF002113),
        secondaryContainer = Color(0xFFD3E8D7), onSecondaryContainer = Color(0xFF202A22),
        tertiary = Color(0xFF3C6472),
        dPrimary = Color(0xFF96D5AE), dOnPrimary = Color(0xFF003824),
        dPrimaryContainer = Color(0xFF115138), dOnPrimaryContainer = Color(0xFFB2F1C9),
        dSecondaryContainer = Color(0xFF3A4C40), dOnSecondaryContainer = Color(0xFFD3E8D7),
        dTertiary = Color(0xFFA4CDDD),
    )

    private val rose = build(
        primary = Color(0xFFB3255F), onPrimary = Color.White,
        primaryContainer = Color(0xFFFFD9E2), onPrimaryContainer = Color(0xFF3E001D),
        secondaryContainer = Color(0xFFF3DDE2), onSecondaryContainer = Color(0xFF2B151A),
        tertiary = Color(0xFF7E5700),
        dPrimary = Color(0xFFFFB1C7), dOnPrimary = Color(0xFF650033),
        dPrimaryContainer = Color(0xFF8E2549), dOnPrimaryContainer = Color(0xFFFFD9E2),
        dSecondaryContainer = Color(0xFF50414A), dOnSecondaryContainer = Color(0xFFF3DDE2),
        dTertiary = Color(0xFFF6BE48),
    )

    private val amber = build(
        primary = Color(0xFF855400), onPrimary = Color.White,
        primaryContainer = Color(0xFFFFDDB3), onPrimaryContainer = Color(0xFF2A1700),
        secondaryContainer = Color(0xFFF2E0CC), onSecondaryContainer = Color(0xFF271904),
        tertiary = Color(0xFF516440),
        dPrimary = Color(0xFFFEB95C), dOnPrimary = Color(0xFF472A00),
        dPrimaryContainer = Color(0xFF653E00), dOnPrimaryContainer = Color(0xFFFFDDB3),
        dSecondaryContainer = Color(0xFF504536), dOnSecondaryContainer = Color(0xFFF2E0CC),
        dTertiary = Color(0xFFB8CEA1),
    )
}
