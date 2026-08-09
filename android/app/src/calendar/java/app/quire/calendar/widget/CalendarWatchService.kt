package app.quire.calendar.widget

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.provider.CalendarContract
import android.provider.Settings

/**
 * Watches for the two things that make a widget's picture wrong: something written to the
 * calendar, and the user changing the phone's colours.
 *
 * Both are content triggers, so there is no polling and no periodic wake-up — a new event appears
 * under its day within seconds, and a new palette lands about as fast. Content-trigger jobs fire
 * once, so the job re-arms itself on every run.
 */
class CalendarWatchService : JobService() {

    override fun onStartJob(params: JobParameters?): Boolean {
        schedule(applicationContext)
        // Whatever woke this, the repaint below uses the colours as they are now, so the app
        // process does not go looking for the same change again next time it starts.
        SchemeWatch.markPainted(applicationContext)
        MonthWidgetProvider.requestUpdate(applicationContext)
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean = false

    companion object {
        private const val JOB_ID = 0x9112

        /**
         * Where the theme picker records the colours it was told to use.
         *
         * The name is not in the SDK's constants, only in the platform's own, so this is a URI to
         * watch rather than a value to read: if a device does not have it, or names it something
         * else, the observer simply never fires and the app-process check still catches up. It is
         * worth the uncertainty because it is the only signal that reaches a widget whose app is
         * not running, which is the case that had people taking the widget off the home screen
         * and putting it back.
         */
        private const val THEME_SETTING = "theme_customization_overlay_packages"

        fun schedule(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
            val job = JobInfo.Builder(
                JOB_ID,
                ComponentName(context, CalendarWatchService::class.java),
            )
                .addTriggerContentUri(
                    JobInfo.TriggerContentUri(
                        CalendarContract.CONTENT_URI,
                        JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS,
                    ),
                )
                .addTriggerContentUri(
                    JobInfo.TriggerContentUri(Settings.Secure.getUriFor(THEME_SETTING), 0),
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
