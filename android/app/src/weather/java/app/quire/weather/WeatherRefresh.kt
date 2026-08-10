package app.quire.weather

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Log
import java.util.concurrent.Executors

/**
 * Keeps the stored forecast current.
 *
 * Weather is the one thing in this app that genuinely has to be polled: nothing on the device
 * changes when the sky does, so there is no content trigger to wait on. An hour is the interval
 * because that is how often the forecast itself is recomputed upstream, and the job carries a
 * network requirement so it is deferred rather than failed when there is nothing to fetch over.
 *
 * The work is deliberately small — one request, one write, one repaint — and it never blocks the
 * job thread's return: [onStartJob] hands the job to an executor and reports that it is still
 * running, which is what lets the system schedule it in a batch with everything else.
 */
class WeatherRefresh : JobService() {

    override fun onStartJob(params: JobParameters?): Boolean {
        EXECUTOR.execute {
            try {
                refreshNow(applicationContext)
            } catch (t: Throwable) {
                Log.w(TAG, "weather refresh failed", t)
            } finally {
                jobFinished(params, false)
            }
        }
        return true
    }

    /** Nothing here is a transaction, so an interrupted run is simply a run that did not happen. */
    override fun onStopJob(params: JobParameters?): Boolean = true

    companion object {
        private const val TAG = "QuireWeather"
        private const val JOB_ID = 0x9113

        private val EXECUTOR = Executors.newSingleThreadExecutor { r ->
            Thread(r, "quire-weather").apply { isDaemon = true }
        }

        /**
         * The shortest interval JobScheduler will honour for periodic work.
         *
         * Below this the platform does not refuse — it silently clamps, which is worse: the app
         * would offer five minutes, the system would run fifteen, and nothing anywhere would say
         * so. Short intervals therefore go through [WeatherTick] and the alarm manager instead.
         */
        const val JOB_FLOOR_MINUTES = 15

        /**
         * Arms the refresh at whatever interval the user asked for, by whichever mechanism can
         * actually deliver it.
         *
         * Called again whenever the interval changes: a periodic job keeps the interval it was
         * scheduled with, so writing the setting without rescheduling changes nothing at all. Both
         * mechanisms are cancelled first, because the interval may have crossed the floor in
         * either direction and two of these running at once would double the fetches.
         */
        fun schedule(context: Context) {
            val minutes = WeatherSettings.get(context).periodMinutes
            cancel(context)
            if (minutes < JOB_FLOOR_MINUTES) {
                WeatherTick.arm(context, minutes)
                return
            }
            val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
            val job = JobInfo.Builder(JOB_ID, ComponentName(context, WeatherRefresh::class.java))
                .setPeriodic(minutes * 60L * 1000L)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .build()
            runCatching { scheduler.schedule(job) }
        }

        fun cancel(context: Context) {
            context.getSystemService(JobScheduler::class.java)?.cancel(JOB_ID)
            WeatherTick.disarm(context)
        }

        /** Asks for a fetch off the caller's thread, for when the app is opened or pulled down. */
        fun request(context: Context, force: Boolean = false, then: (Forecast?) -> Unit = {}) {
            val app = context.applicationContext
            EXECUTOR.execute {
                val forecast = runCatching { refreshNow(app, force) }.getOrNull()
                then(forecast)
            }
        }

        /**
         * Fetches if there is anywhere to fetch for and the stored copy is old enough to bother.
         *
         * Returns whatever the app should now show — the new forecast, or the stored one if the
         * fetch was skipped or failed. Runs on the caller's thread; every caller is a background
         * one.
         */
        fun refreshNow(context: Context, force: Boolean = false): Forecast? {
            val stored = WeatherStore.load(context)
            val now = System.currentTimeMillis()
            // How old is too old is the interval the user chose, not a constant. It used to be a
            // flat forty-five minutes, which was invisible while the shortest interval was an
            // hour and would have swallowed every tick the moment five minutes was offered: the
            // alarm would fire on time and the fetch would decline, nine times out of ten.
            val staleAfter = staleAfterMillis(context)
            val fresh = stored != null && now - stored.fetched < staleAfter
            if (fresh && !force) return stored

            val remembered = WeatherStore.lastPlace(context)
            // A named place is not re-derived from where the phone happens to be.
            val fix = if (WeatherStore.pinned(context)) null else Whereabouts.last(context)
            val latitude = fix?.latitude ?: remembered?.second
            val longitude = fix?.longitude ?: remembered?.third
            if (latitude == null || longitude == null) return stored

            // The name is only looked up again when the position moved enough to have a different
            // one; a geocode is a network round trip of its own and the answer rarely changes.
            val place = when {
                WeatherStore.pinned(context) -> remembered?.first.orEmpty()
                remembered != null && remembered.first.isNotBlank() &&
                    near(latitude, longitude, remembered.second, remembered.third) -> remembered.first
                else -> Whereabouts.name(context, latitude, longitude)
            }

            return runCatching {
                WeatherRepository.fetch(latitude, longitude, place, now).also {
                    WeatherStore.save(context, it)
                    RainAlert.consider(context, it)
                }
            }.getOrElse { stored }
        }

        /**
         * The age at which the stored forecast is worth replacing.
         *
         * The floor is there for the other caller: opening the app refreshes, and without one a
         * handful of app switches would each be a request.
         */
        fun staleAfterMillis(context: Context): Long {
            val minutes = WeatherSettings.get(context).periodMinutes
            return maxOf(minutes, MIN_STALE_MINUTES) * 60L * 1000L
        }

        private const val MIN_STALE_MINUTES = 2

        /** Within about a kilometre, which is the resolution the forecast is asked for anyway. */
        private fun near(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Boolean =
            Math.abs(aLat - bLat) < 0.02 && Math.abs(aLon - bLon) < 0.02
    }
}
