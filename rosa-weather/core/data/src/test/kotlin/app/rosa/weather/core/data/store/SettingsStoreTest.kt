package app.rosa.weather.core.data.store

import app.rosa.weather.core.model.AppSettings
import app.rosa.weather.core.model.Appearance
import app.rosa.weather.core.model.HapticsLevel
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** The settings file across versions: old and newer files read, and nothing else is lost with them. */
class SettingsStoreTest {
    private val serializer = JsonSerializer(AppSettings.serializer(), AppSettings())

    private suspend fun read(json: String): AppSettings = serializer.readFrom(ByteArrayInputStream(json.toByteArray()))

    @Test
    fun `settings are kept`() = runTest {
        val out = ByteArrayOutputStream()
        serializer.writeTo(AppSettings(appearance = Appearance.Evening, haptics = HapticsLevel.Subtle), out)
        val back = read(out.toString())
        assertThat(back.appearance).isEqualTo(Appearance.Evening)
        assertThat(back.haptics).isEqualTo(HapticsLevel.Subtle)
    }

    /**
     * 2.7.0 had two more appearances, AMOLED and tinted glass, and the hue of the glass. After them
     * the app is lit by the real sky again, and the rest of the settings stay as they were.
     */
    @Test
    fun `the appearances of 2_7_0 read as auto`() = runTest {
        for (removed in listOf("Amoled", "Tinted")) {
            val back = read("""{"appearance":"$removed","glassHue":205,"haptics":"Subtle","tiltLighting":false}""")
            assertThat(back.appearance).isEqualTo(Appearance.Auto)
            assertThat(back.haptics).isEqualTo(HapticsLevel.Subtle)
            assertThat(back.tiltLighting).isFalse()
        }
    }
}
