package de.isolarwidget.worker

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import de.isolarwidget.data.PvRepository
import de.isolarwidget.ui.widget.PvWidget

/**
 * Holt die Werte, schreibt den Cache und rendert danach das Widget neu.
 * Fehler werden im Cache vermerkt; der Worker meldet trotzdem success, damit WorkManager
 * nicht zusätzlich in kurzen Abständen wiederholt (Rate-Limits/Akku). Der nächste
 * periodische Lauf versucht es erneut.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = PvRepository.get(applicationContext)
        try {
            repo.sync()
        } finally {
            repo.cache.setSyncing(false)
            PvWidget().updateAll(applicationContext)
        }
        return Result.success()
    }
}
