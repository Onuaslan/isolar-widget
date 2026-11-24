package de.isolarwidget.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object SyncScheduler {
    private const val PERIODIC = "pv_sync_periodic"
    private const val ONE_TIME = "pv_sync_now"

    /** iSolarCloud aktualisiert Echtzeitdaten etwa alle 5 Min.; 30 Min. schont Akku und Rate-Limits. */
    private const val INTERVAL_MIN = 30L

    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(INTERVAL_MIN, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /**
     * Manueller Refresh. Bewusst OHNE Netzwerk-Constraint: ohne Verbindung soll der Sync
     * sofort kontrolliert fehlschlagen und das Widget "nicht aktuell" zeigen, statt still zu warten.
     */
    fun syncNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            ONE_TIME, ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<SyncWorker>().build(),
        )
    }

    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
        WorkManager.getInstance(context).cancelUniqueWork(ONE_TIME)
    }
}
