package app.rosa.weather.core.data.store

import app.rosa.weather.core.model.AppSettings
import app.rosa.weather.core.model.Appearance
import app.rosa.weather.core.model.GlassTint
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** The settings file across versions: the new appearances are kept, and old or newer files still read. */
class SettingsStoreTest {
    private val serializer = JsonSerializer(AppSettings.serializer(), AppSettings())

    private suspend fun read(json: String): AppSettings = serializer.readFrom(ByteArrayInputStream(json.toByteArray()))

    @Test
    fun `the tinted glass and its hue are kept`() = runTest {
        val out = ByteArrayOutputStream()
        serializer.writeTo(AppSettings(appearance = Appearance.Tinted, glassHue = 205), out)
        val back = read(out.toString())
        assertThat(back.appearance).isEqualTo(Appearance.Tinted)
        assertThat(back.glassHue).isEqualTo(205)
    }

    @Test
    fun `a file from before the tinted glass gets the default hue`() = runTest {
        val back = read("""{"appearance":"Dark","haptics":"Subtle"}""")
        assertThat(back.appearance).isEqualTo(Appearance.Dark)
        assertThat(back.glassHue).isEqualTo(GlassTint.DEFAULT_HUE)
    }

    /** Going back to an older version never throws the settings away over a mode it doesn't know. */
    @Test
    fun `an appearance this version does not know reads as auto`() = runTest {
        val back = read("""{"appearance":"Aurora","haptics":"Subtle"}""")
        assertThat(back.appearance).isEqualTo(Appearance.Auto)
        assertThat(back.haptics.name).isEqualTo("Subtle")
    }
}
