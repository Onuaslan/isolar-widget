package de.isolarwidget.data.cache

enum class SyncStatus { OK, STALE, ERROR }

/** Werte, wie sie die API für eine Anlage liefert (null = Messpunkt nicht vorhanden). */
data class PvValues(
    val pvPowerW: Double?,
    val batterySocPct: Double?,
    /** Positiv = Netzbezug, negativ = Einspeisung. */
    val gridPowerW: Double?,
    val dailyYieldKwh: Double?,
)

/** Gesamter Cache-Inhalt, den Widget und App lesen. */
data class PvSnapshot(
    val values: PvValues?,
    val lastUpdatedEpochMs: Long?,
    val lastAttemptEpochMs: Long?,
    val status: SyncStatus,
    val message: String?,
    val syncing: Boolean,
) {
    /** Auch ohne Fehler gilt ein Stand als veraltet, wenn er zu alt ist. */
    fun isOutdated(now: Long = System.currentTimeMillis()): Boolean =
        status != SyncStatus.OK || lastUpdatedEpochMs == null || now - lastUpdatedEpochMs > MAX_AGE_MS

    companion object {
        const val MAX_AGE_MS = 90 * 60 * 1000L
        val EMPTY = PvSnapshot(null, null, null, SyncStatus.ERROR, "Noch keine Daten", false)
    }
}
