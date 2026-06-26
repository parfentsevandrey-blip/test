package com.claude.tokenwidget.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.claude.tokenwidget.data.UsageRepository
import java.util.concurrent.TimeUnit

/**
 * Refreshes the cached usage snapshot and re-renders every widget instance.
 *
 * Runs on a 15-minute WorkManager period (the platform minimum) so the widget
 * "dynamically updates" without keeping the app alive, and can also be kicked
 * immediately (on add, or via the refresh button).
 */
class UsageWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            UsageRepository(applicationContext).refresh(System.currentTimeMillis())
            TokenWidget().updateAll(applicationContext)
            Result.success()
        } catch (t: Throwable) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val PERIODIC = "claude_usage_periodic"
        private const val ONCE = "claude_usage_once"

        fun enqueuePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<UsageWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun enqueueOnce(context: Context) {
            val request = OneTimeWorkRequestBuilder<UsageWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONCE,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
        }
    }
}

/** Refresh button on the large widget layout: kick an immediate refresh. */
class RefreshAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        UsageRepository(context).refresh(System.currentTimeMillis())
        TokenWidget().update(context, glanceId)
    }
}

/** Convenience used by the config screen to push an immediate re-render. */
suspend fun TokenWidget.updateEveryInstance(context: Context) {
    val ids = GlanceAppWidgetManager(context).getGlanceIds(TokenWidget::class.java)
    ids.forEach { update(context, it) }
}
