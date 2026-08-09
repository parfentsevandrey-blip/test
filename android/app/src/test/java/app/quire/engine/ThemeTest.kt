package app.quire.engine

import app.quire.engine.design.Metrics
import app.quire.engine.design.Oklch
import app.quire.engine.design.SystemScheme
import app.quire.engine.design.Theme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The design engine derives an entire palette from one colour, which is only worth doing if the
 * result is guaranteed legible. These tests are that guarantee: every seed the app offers, in
 * both modes, at both ends of the contrast range.
 *
 * Plain JVM, no Android — colour is arithmetic, and it should not need a device to check.
 */
class ThemeTest {

    /** Every seed the settings sheet offers, plus a few that are deliberately awkward. */
    private fun seeds(): List<Pair<String, Int>> = Theme.seeds + listOf(
        // A near-grey: the categorical hues have to stay distinguishable even with no chroma to
        // borrow from, which is the case the palette is most likely to collapse in.
        "flat grey" to 0xFF808080.toInt(),
        "almost black" to 0xFF0A0A0A.toInt(),
        "almost white" to 0xFFF7F7F7.toInt(),
        "saturated cyan" to 0xFF00E5FF.toInt(),
    )

    private fun eachTheme(block: (String, Theme) -> Unit) {
        seeds().forEach { (name, seed) ->
            listOf(false, true).forEach { dark ->
                listOf(0f, 0.5f, 1f).forEach { boost ->
                    block("$name dark=$dark boost=$boost", Theme(seed, dark, boost))
                }
            }
        }
    }

    @Test
    fun `body text always clears the contrast it claims`() {
        eachTheme { label, theme ->
            // 7:1 is AAA for body text. The ink tier is the one everything readable is set in,
            // so it gets the strict bar; the quieter tiers get theirs below.
            val ink = Oklch.contrast(theme.ink, theme.canvas)
            assertTrue("$label: ink on canvas is only ${"%.2f".format(ink)}:1", ink >= 7f)

            val muted = Oklch.contrast(theme.inkMuted, theme.canvas)
            assertTrue("$label: inkMuted is only ${"%.2f".format(muted)}:1", muted >= 4.4f)

            val faint = Oklch.contrast(theme.inkFaint, theme.canvas)
            assertTrue("$label: inkFaint is only ${"%.2f".format(faint)}:1", faint >= 2.9f)
        }
    }

    @Test
    fun `text on the accent is readable, whatever the seed`() {
        eachTheme { label, theme ->
            val onAccent = Oklch.contrast(theme.onAccent, theme.accent)
            // 4.5:1 is AA for body text, and the accent carries the today number and the label of
            // whichever bar entry is live — small text on a saturated ground, the hardest case.
            assertTrue(
                "$label: onAccent is only ${"%.2f".format(onAccent)}:1",
                onAccent >= 4.4f,
            )
            val accentOnCanvas = Oklch.contrast(theme.accent, theme.canvas)
            assertTrue(
                "$label: the accent itself is only ${"%.2f".format(accentOnCanvas)}:1",
                accentOnCanvas >= 2.9f,
            )
        }
    }

    @Test
    fun `the three planes stay distinguishable from one another`() {
        eachTheme { label, theme ->
            assertNotEquals("$label: surface equals canvas", theme.canvas, theme.surface)
            assertNotEquals("$label: lifted equals surface", theme.surface, theme.surfaceLifted)
        }
    }

    @Test
    fun `categorical marks stay distinct even from a colourless seed`() {
        eachTheme { label, theme ->
            val seen = HashSet<Int>()
            var index = 0
            while (index < 8) {
                val colour = theme.categorical(index)
                assertTrue(
                    "$label: category $index repeats a colour",
                    seen.add(colour),
                )
                val against = Oklch.contrast(colour, theme.canvas)
                assertTrue(
                    "$label: category $index is only ${"%.2f".format(against)}:1",
                    against >= 2.4f,
                )
                index++
            }
        }
    }

    @Test
    fun `a negative or out-of-range category index still answers`() {
        val theme = Theme(Theme.seeds[0].second, dark = false)
        // Callers index this by list position, and a list position can be anything.
        assertEquals(theme.categorical(0), theme.categorical(-0))
        assertTrue(theme.categorical(-3) != 0)
        assertTrue(theme.categorical(9999) != 0)
    }

    @Test
    fun `two themes built from the same inputs are equal, so a rebuild can be skipped`() {
        val a = Theme(0xFFC0402B.toInt(), dark = false, contrastBoost = 0.3f)
        val b = Theme(0xFFC0402B.toInt(), dark = false, contrastBoost = 0.3f)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, Theme(0xFFC0402B.toInt(), dark = true, contrastBoost = 0.3f))
        assertNotEquals(a, Theme(0xFF2E4A7D.toInt(), dark = false, contrastBoost = 0.3f))
    }

    @Test
    fun `raising the contrast never lowers it`() {
        seeds().forEach { (name, seed) ->
            listOf(false, true).forEach { dark ->
                val plain = Theme(seed, dark, 0f)
                val boosted = Theme(seed, dark, 1f)
                val before = Oklch.contrast(plain.ink, plain.canvas)
                val after = Oklch.contrast(boosted.ink, boosted.canvas)
                assertTrue(
                    "$name dark=$dark: boosting took ${"%.2f".format(before)} to " +
                        "${"%.2f".format(after)}",
                    after >= before - 0.01f,
                )
            }
        }
    }

    @Test
    fun `srgb survives the round trip through oklch`() {
        val lch = FloatArray(3)
        var worst = 0
        var step = 0
        // A lattice through the cube rather than every colour: enough to catch a broken
        // conversion, quick enough to run on every build.
        while (step < 18 * 18 * 18) {
            val r = (step % 18) * 15
            val g = ((step / 18) % 18) * 15
            val b = ((step / (18 * 18)) % 18) * 15
            val colour = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            Oklch.fromSrgb(colour, lch)
            val back = Oklch.toSrgb(lch[0], lch[1], lch[2])
            worst = maxOf(
                worst,
                maxOf(
                    Math.abs(((back shr 16) and 0xFF) - r),
                    Math.abs(((back shr 8) and 0xFF) - g),
                    Math.abs((back and 0xFF) - b),
                ),
            )
            step++
        }
        assertTrue("worst round-trip channel error was $worst", worst <= 1)
    }

    /**
     * A scheme every role of which is legible is adopted whole: the app then wears the device's
     * Material colours exactly rather than an approximation of them.
     */
    @Test
    fun `a legible system scheme is adopted rather than approximated`() {
        val scheme = SystemScheme(
            primary = 0xFF3F5F9E.toInt(),
            onPrimary = 0xFFFFFFFF.toInt(),
            primaryContainer = 0xFFD8E2FF.toInt(),
            secondaryContainer = 0xFFDCE2F9.toInt(),
            tertiaryContainer = 0xFFFAD8FD.toInt(),
            surface = 0xFFFAF8FF.toInt(),
            surfaceContainer = 0xFFEEEDF4.toInt(),
            surfaceContainerHigh = 0xFFE8E7EF.toInt(),
            onSurface = 0xFF1A1B21.toInt(),
            onSurfaceVariant = 0xFF44464F.toInt(),
            outline = 0xFF757780.toInt(),
            outlineVariant = 0xFFC5C6D0.toInt(),
        )
        val theme = Theme(0xFFC0402B.toInt(), dark = false, scheme = scheme)

        assertEquals("the page is not the system's surface", scheme.surface, theme.canvas)
        assertEquals("the card is not the system's container", scheme.surfaceContainer, theme.surface)
        assertEquals("the ink is not the system's onSurface", scheme.onSurface, theme.ink)
        assertEquals("the accent is not the system's primary", scheme.primary, theme.accent)
        assertEquals("onAccent is not the system's onPrimary", scheme.onPrimary, theme.onAccent)
        assertEquals("the rule is not the system's outline", scheme.outline, theme.hairlineStrong)

        // Adopting must not cost the promise the palette makes on its own.
        assertTrue(
            "adopted ink is only ${"%.2f".format(Oklch.contrast(theme.ink, theme.canvas))}:1",
            Oklch.contrast(theme.ink, theme.canvas) >= 7f,
        )
    }

    /**
     * And a scheme that would not be legible is refused role by role. A vendor scheme, or a
     * contrast setting the user has asked for, must not be able to quietly cost the calendar its
     * readability — the walk that cannot fail takes over instead.
     */
    @Test
    fun `an illegible system scheme is refused`() {
        val grey = 0xFF8A8A8A.toInt()
        val scheme = SystemScheme(
            // onSurface identical to surface: nothing could be read on it.
            primary = grey,
            onPrimary = grey,
            primaryContainer = grey,
            secondaryContainer = grey,
            tertiaryContainer = grey,
            surface = grey,
            surfaceContainer = grey,
            surfaceContainerHigh = grey,
            onSurface = grey,
            onSurfaceVariant = grey,
            outline = grey,
            outlineVariant = grey,
        )
        val theme = Theme(0xFFC0402B.toInt(), dark = false, scheme = scheme)

        assertNotEquals("illegible ink was adopted anyway", grey, theme.ink)
        assertNotEquals("an invisible accent was adopted anyway", grey, theme.accent)
        assertTrue(
            "the fallback ink is only ${"%.2f".format(Oklch.contrast(theme.ink, theme.canvas))}:1",
            Oklch.contrast(theme.ink, theme.canvas) >= 7f,
        )
        assertTrue(
            "the fallback onAccent is only " +
                "${"%.2f".format(Oklch.contrast(theme.onAccent, theme.accent))}:1",
            Oklch.contrast(theme.onAccent, theme.accent) >= 4.4f,
        )
    }

    @Test
    fun `a theme remembers which scheme it was built from`() {
        val a = Theme(0xFFC0402B.toInt(), dark = false)
        val b = Theme(
            0xFFC0402B.toInt(),
            dark = false,
            scheme = SystemScheme(
                primary = 0xFF3F5F9E.toInt(),
                onPrimary = 0xFFFFFFFF.toInt(),
                primaryContainer = 0xFFD8E2FF.toInt(),
                secondaryContainer = 0xFFDCE2F9.toInt(),
                tertiaryContainer = 0xFFFAD8FD.toInt(),
                surface = 0xFFFAF8FF.toInt(),
                surfaceContainer = 0xFFEEEDF4.toInt(),
                surfaceContainerHigh = 0xFFE8E7EF.toInt(),
                onSurface = 0xFF1A1B21.toInt(),
                onSurfaceVariant = 0xFF44464F.toInt(),
                outline = 0xFF757780.toInt(),
                outlineVariant = 0xFFC5C6D0.toInt(),
            ),
        )
        // Otherwise a wallpaper change would be cached away and never repaint.
        assertNotEquals(a, b)
    }

    @Test
    fun `metrics scale everything except the hairline`() {
        val plain = Metrics(3f)
        val large = Metrics(3f, scale = 1.25f)
        assertEquals(plain.dp(16f) * 1.25f, large.dp(16f), 0.001f)
        assertEquals(plain.sp(14f) * 1.25f, large.sp(14f), 0.001f)
        // A rule is a rule at any size; scaling a hairline only turns it grey.
        assertEquals(plain.hairline, large.hairline, 0.001f)

        // A display that has not reported itself yet must not collapse every size to nothing.
        val uninitialised = Metrics(0f)
        assertTrue(uninitialised.dp(16f) > 0f)
        assertTrue(uninitialised.hairline >= 1f)
    }
}
