package app.quire.calendar

import android.app.Application
import android.app.job.JobScheduler
import android.content.Intent
import android.provider.CalendarContract
import androidx.test.core.app.ApplicationProvider
import app.quire.calendar.core.Prefs
import app.quire.calendar.widget.CalendarWatchService
import app.quire.calendar.widget.MonthWidgetProvider
import app.quire.calendar.widget.SchemeWatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * What wakes a widget.
 *
 * A widget is a picture the launcher keeps; it only changes when something asks the provider for a
 * new one. These are the asks that are not obvious from reading the provider — the ones that used
 * to be missing, and that a user only notices as a widget stuck in last week's colours.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WidgetWakeTest {

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    private fun refreshes(): Int = shadowOf(app).broadcastIntents
        .count { it.action == MonthWidgetProvider.ACTION_REFRESH }

    /**
     * The colours are compared rather than assumed changed, because everything that reconfigures
     * the process arrives at the same check — every rotation, every font-scale change, every
     * launch — and a repaint is a cross-process calendar query.
     */
    @Test
    fun `a moved colour scheme repaints once, and an unmoved one not at all`() {
        val prefs = Prefs.get(app)
        // Stand in for "the colours are not the ones these widgets were painted in".
        prefs.paintedScheme = SOMETHING_ELSE

        val before = refreshes()
        SchemeWatch.repaintIfChanged(app)
        assertEquals("a changed scheme did not ask for a repaint", before + 1, refreshes())

        SchemeWatch.repaintIfChanged(app)
        SchemeWatch.repaintIfChanged(app)
        assertEquals(
            "an unchanged scheme asked for a repaint anyway",
            before + 1,
            refreshes(),
        )
    }

    /**
     * The job is the only one of the two paths that reaches a widget whose app is not running,
     * which is the ordinary case: nobody opens a calendar app to change their wallpaper.
     */
    @Test
    fun `the watch job wakes on the calendar and on the theme`() {
        CalendarWatchService.schedule(app)
        val jobs = app.getSystemService(JobScheduler::class.java).allPendingJobs
        val triggers = jobs.flatMap { it.triggerContentUris?.toList().orEmpty() }
            .map { it.uri.toString() }

        assertTrue(
            "nothing watches the calendar: $triggers",
            triggers.any { it.startsWith(CalendarContract.CONTENT_URI.toString()) },
        )
        assertTrue(
            "nothing watches the setting the theme picker writes: $triggers",
            triggers.any { it.endsWith("theme_customization_overlay_packages") },
        )
        // The fingerprint always included the night mode; nothing ever fired when only the night
        // mode changed. A widget following the system stayed in yesterday's half of the scheme
        // until some other wake happened along.
        assertTrue(
            "nothing watches the setting the dark-theme switch writes: $triggers",
            triggers.any { it.endsWith("ui_night_mode") },
        )
    }

    /**
     * A job keeps the triggers it was scheduled with. The theme trigger was added in an update, so
     * without this an already-placed widget would never get it.
     */
    @Test
    fun `replacing the package re-arms the watch`() {
        app.getSystemService(JobScheduler::class.java).cancelAll()

        MonthWidgetProvider().onReceive(app, Intent(Intent.ACTION_MY_PACKAGE_REPLACED))
        repeat(20) {
            shadowOf(android.os.Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }

        val jobs = app.getSystemService(JobScheduler::class.java).allPendingJobs
        assertTrue("the watch job was not rescheduled", jobs.isNotEmpty())
    }
}

/** Any value the real fingerprint will not be; only its difference matters. */
private const val SOMETHING_ELSE = 0x5EED
