package de.isolarwidget

import android.app.Application
import de.isolarwidget.data.PvRepository
import de.isolarwidget.worker.SyncScheduler

class IsolarApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (PvRepository.get(this).isConfigured) SyncScheduler.schedulePeriodic(this)
    }
}
