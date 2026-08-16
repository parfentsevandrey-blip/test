package app.quire.weather

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.provider.Settings

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
        schedule(applicationContext)
        // A broadcast to the provider rather than a render on this thread: the provider already
        // owns a background lane for painting, and the job can finish immediately.
        WeatherWidgetProvider.requestUpdate(applicationContext)
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean = false

    companion object {
        private const val JOB_ID = 0x9114

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
        }

        fun cancel(context: Context) {
            context.getSystemService(JobScheduler::class.java)?.cancel(JOB_ID)
        }
    }
}
