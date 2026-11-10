package com.transitopia.bartwidget.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class RefreshWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        BartWidgetProvider.refreshAll(applicationContext)
        return Result.success()
    }
}

object WidgetRefreshScheduler {
    // WorkManager's periodic minimum is 15 min, so also refresh on tap (see provider)
    // and expose manual refresh in MainActivity. 15 min is the battery-safe cadence.
    fun schedule(context: Context) {
        val req = PeriodicWorkRequestBuilder<RefreshWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "bart-widget-refresh", ExistingPeriodicWorkPolicy.UPDATE, req)
    }
}
