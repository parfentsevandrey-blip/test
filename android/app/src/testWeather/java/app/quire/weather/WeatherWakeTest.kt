package app.quire.weather

import android.app.Application
import android.app.job.JobScheduler
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import app.quire.R
import app.quire.calendar.core.Prefs
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

        // And the net under the triggers: a periodic pulse that compares the look and repaints
        // only when it moved, for the theme flip no setting announces.
        val periodic = app.getSystemService(JobScheduler::class.java).allPendingJobs
            .any { it.isPeriodic }
        assertTrue("there is no pulse under the triggers", periodic)
    }

    /**
     * The launcher itself winds the clock.
     *
     * Everything else here is our own scheduling, and our own scheduling is subject to App
     * Standby: a sideloaded app nobody opens lands in the restricted bucket, where a periodic
     * job can be deferred for days — which is how the card sat for a week saying Tuesday while
     * every test of the job's *logic* passed. updatePeriodMillis is the one wake the buckets do
     * not touch, so it is the floor under all of it, and it is asserted here so nobody trades it
     * away for a tidier-looking zero again.
     */
    @Test
    fun `the launcher itself winds the clock`() {
        val parser = app.resources.getXml(R.xml.weather_widget_info)
        var period = 0L
        while (parser.eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == org.xmlpull.v1.XmlPullParser.START_TAG &&
                parser.name == "appwidget-provider"
            ) {
                period = parser.getAttributeIntValue(
                    "http://schemas.android.com/apk/res/android",
                    "updatePeriodMillis",
                    0,
                ).toLong()
            }
            parser.next()
        }
        assertTrue("the launcher never wakes the card (updatePeriodMillis=$period)", period >= 1_800_000L)
    }

    /**
     * The day columns are a toggle: the same tap that opens a day on the card closes it. The
     * state lives in the widget's own prefs, so two placed cards can hold two different days
     * open — and the timestamp is what lets an abandoned peek expire on its own.
     */
    @Test
    fun `tapping a day holds it, and tapping it again lets it go`() {
        val widgetId = 55
        fun tap() {
            WeatherWidgetProvider().onReceive(
                app,
                Intent(app, WeatherWidgetProvider::class.java)
                    .setAction(WeatherWidgetProvider.ACTION_PEEK)
                    .putExtra(
                        android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID,
                        widgetId,
                    )
                    .putExtra(WeatherWidgetProvider.EXTRA_DAY, "2026-08-21"),
            )
            repeat(20) {
                shadowOf(android.os.Looper.getMainLooper()).idle()
                Thread.sleep(20)
            }
        }

        val wp = Prefs.get(app).widget(widgetId)
        tap()
        assertTrue("the tap did not hold the day: '" + wp.peekDay + "'", wp.peekDay == "2026-08-21")
        assertTrue("the peek carries no clock to expire by", wp.peekAt > 0L)

        tap()
        assertTrue("the second tap did not let the day go: '" + wp.peekDay + "'", wp.peekDay.isEmpty())
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
