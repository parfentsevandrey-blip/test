package app.quire.calendar.widget

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.provider.CalendarContract

/**
 * Watches the calendar provider for writes and repaints the widgets when
 * something changes — a new event appears under its day within seconds, with no
 * polling and no periodic wake-ups. Content-trigger jobs fire once, so the job
 * re-arms itself on every run.
 */
class CalendarWatchService : JobService() {

    override fun onStartJob(params: JobParameters?): Boolean {
        schedule(applicationContext)
        MonthWidgetProvider.requestUpdate(applicationContext)
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean = false

    companion object {
        private const val JOB_ID = 0x9112

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
