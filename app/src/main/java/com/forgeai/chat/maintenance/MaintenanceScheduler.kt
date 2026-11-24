package com.forgeai.chat.maintenance

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Schedules privacy-preserving maintenance. WorkManager is inexact by design:
 * Android may defer jobs for Doze, battery, and connectivity constraints.
 */
object MaintenanceScheduler {
    private const val DAILY_HEALTH_WORK = "forgeai_daily_health_check"
    private const val MONTHLY_DISCOVERY_WORK = "forgeai_monthly_feature_discovery"

    fun schedule(context: Context) {
        val manager = WorkManager.getInstance(context.applicationContext)

        val healthRequest = PeriodicWorkRequestBuilder<DailyHealthWorker>(
            1, TimeUnit.DAYS
        ).build()
        manager.enqueueUniquePeriodicWork(
            DAILY_HEALTH_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            healthRequest
        )

        val networkConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val discoveryRequest = PeriodicWorkRequestBuilder<MonthlyFeatureDiscoveryWorker>(
            30, TimeUnit.DAYS
        ).setConstraints(networkConstraints).build()
        manager.enqueueUniquePeriodicWork(
            MONTHLY_DISCOVERY_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            discoveryRequest
        )
    }
}
