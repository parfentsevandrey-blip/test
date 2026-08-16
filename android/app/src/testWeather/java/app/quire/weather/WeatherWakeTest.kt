package app.quire.weather

import android.app.Application
import android.app.job.JobScheduler
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * What wakes the weather card.
 *
 * A widget is a picture the launcher keeps; it only changes when something asks the provider for
 * a new one. Each test here is a wake that was missing in a shipped build, found the only way a
 * missing wake is ever found — a card on a real home screen showing last week.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WeatherWakeTest {

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    private fun repaints(): Int = shadowOf(app).broadcastIntents
        .count { it.action == WeatherWidgetProvider.ACTION_REFRESH }

    /**
     * The hourly job has to end by asking for a repaint.
     *
     * It fetched and saved faithfully for several releases and never told the launcher, so the
     * app always opened on fresh numbers while the card on the home screen showed the forecast
     * from whenever the app was last opened. A background refresh whose output nobody draws is no
     * refresh at all on exactly the surface it exists for.
     */
    @Test
    fun `the refresh job ends by repainting the card`() {
        val before = repaints()
        WeatherRefresh.runOnce(app)
        assertTrue("the job refreshed and told nobody", repaints() == before + 1)
    }

    /** The watch has to hold both settings: the palette picker's, and the dark-theme switch's. */
    @Test
    fun `the watch job wakes on the palette and on the dark switch`() {
        WeatherWatch.schedule(app)
        val triggers = app.getSystemService(JobScheduler::class.java).allPendingJobs
            .flatMap { it.triggerContentUris?.toList().orEmpty() }
            .map { it.uri.toString() }

        assertTrue(
            "nothing watches the setting the palette picker writes: $triggers",
            triggers.any { it.endsWith("theme_customization_overlay_packages") },
        )
        assertTrue(
            "nothing watches the setting the dark-theme switch writes: $triggers",
            triggers.any { it.endsWith("ui_night_mode") },
        )
    }

    /**
     * A job keeps the triggers it was scheduled with, so an update that adds the watch would
     * never reach an already-placed card unless the package-replaced broadcast re-arms it.
     */
    @Test
    fun `replacing the package arms the watch`() {
        app.getSystemService(JobScheduler::class.java).cancelAll()

        WeatherWidgetProvider().onReceive(app, Intent(Intent.ACTION_MY_PACKAGE_REPLACED))
        repeat(20) {
            shadowOf(android.os.Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }

        val jobs = app.getSystemService(JobScheduler::class.java).allPendingJobs
        assertTrue(
            "the watch job was not scheduled by the update",
            jobs.any { (it.triggerContentUris?.size ?: 0) > 0 },
        )
    }
}
