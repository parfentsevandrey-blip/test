package app.quire.weather

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import android.provider.Settings
import app.quire.engine.design.SystemScheme

/**
 * Notices that the phone's look has moved under a placed weather card, and repaints it.
 *
 * A widget is a picture the launcher holds on to. The renderer computes its palette when it
 * builds that picture and bakes the colours in, so the picture keeps the colours it was painted
 * with until something asks for a new one — and neither changing the wallpaper's palette nor
 * flipping dark mode asks anybody. The calendar has watched for this for a while;
 * the weather card shipped without a watcher at all, which is why it sat in yesterday's colours
 * while the calendar beside it changed.
 *
 * There is no broadcast for any of it — `ACTION_CONFIGURATION_CHANGED` cannot be delivered to a
 * manifest receiver — so the change is caught as content triggers on the two settings the system
 * writes when it happens: the overlay list the palette picker records, and the night-mode flag
 * the dark-theme switch records. Content-trigger jobs fire once, so the job re-arms itself on
 * every run. The hourly refresh repaints too, as a cap for anything a trigger misses: a scheduled
 * sunset switch, a device that names the settings differently.
 */
class WeatherWatch : JobService() {

    override fun onStartJob(params: JobParameters?): Boolean {
        if (params?.jobId == PULSE_ID) {
            // The half-hour pulse: compare, and only repaint when the look actually moved. This
            // is the net under the trigger — a device that names the settings differently, or a
            // dark theme flipped by schedule rather than by hand, still lands within the half
            // hour. The comparison is two ints, so a quiet pulse costs nothing.
            repaintIfLookChanged(applicationContext)
            return false
        }
        schedule(applicationContext)
        // A broadcast to the provider rather than a render on this thread: the provider already
        // owns a background lane for painting, and the job can finish immediately.
        WeatherWidgetProvider.requestUpdate(applicationContext)
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean = false

    companion object {
        private const val JOB_ID = 0x9114
        private const val PULSE_ID = 0x9116
        private const val PULSE_MINUTES = 30L

        /** Where the palette picker records its choice; not in the SDK's constants, only the platform's. */
        private const val THEME_SETTING = "theme_customization_overlay_packages"

        /** Where the dark-theme switch records its choice, same caveat. */
        private const val NIGHT_SETTING = "ui_night_mode"

        fun schedule(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
            val job = JobInfo.Builder(JOB_ID, ComponentName(context, WeatherWatch::class.java))
                .addTriggerContentUri(
                    JobInfo.TriggerContentUri(Settings.Secure.getUriFor(THEME_SETTING), 0),
                )
                .addTriggerContentUri(
                    JobInfo.TriggerContentUri(Settings.Secure.getUriFor(NIGHT_SETTING), 0),
                )
                .setTriggerContentUpdateDelay(2_000L)
                .setTriggerContentMaxDelay(30_000L)
                .build()
            runCatching { scheduler.schedule(job) }

            val pulse = JobInfo.Builder(PULSE_ID, ComponentName(context, WeatherWatch::class.java))
                .setPeriodic(PULSE_MINUTES * 60_000L)
                .setPersisted(true)
                .build()
            runCatching { scheduler.schedule(pulse) }
        }

        fun cancel(context: Context) {
            context.getSystemService(JobScheduler::class.java)?.cancel(JOB_ID)
            context.getSystemService(JobScheduler::class.java)?.cancel(PULSE_ID)
        }

        /** Repaints the card if the phone's look is not the one it was painted in. */
        fun repaintIfLookChanged(context: Context) {
            val settings = WeatherSettings.get(context)
            val now = lookFingerprint(context)
            if (now == settings.paintedLook) return
            settings.paintedLook = now
            WeatherWidgetProvider.requestUpdate(context)
        }

        /** Records the look as painted, for a repaint that happened for some other reason. */
        fun markPainted(context: Context) {
            WeatherSettings.get(context).paintedLook = lookFingerprint(context)
        }

        /** The night half of the config and both halves of the wallpaper scheme, in one number. */
        private fun lookFingerprint(context: Context): Int {
            var hash = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            if (SystemScheme.supported) {
                hash = 31 * hash + (SystemScheme.read(context, dark = false)?.hashCode() ?: 0)
                hash = 31 * hash + (SystemScheme.read(context, dark = true)?.hashCode() ?: 0)
            }
            return hash
        }
    }
}
