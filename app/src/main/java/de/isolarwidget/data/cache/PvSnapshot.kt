package de.isolarwidget.data.cache

enum class SyncStatus { OK, STALE, ERROR }

/** Werte, wie sie die API für eine Anlage liefert (null = Messpunkt nicht vorhanden). */
data class PvValues(
    val pvPowerW: Double?,
    val batterySocPct: Double?,
    /** Positiv = Netzbezug, negativ = Einspeisung. */
    val gridPowerW: Double?,
    val dailyYieldKwh: Double?,
    /** Aktuelle Hauslast (W). */
    val loadPowerW: Double? = null,
    /** Tageswerte in kWh. */
    val consumptionTodayKwh: Double? = null,
    val gridBuyTodayKwh: Double? = null,
    val feedInTodayKwh: Double? = null,
    val batteryChargeTodayKwh: Double? = null,
    val batteryDischargeTodayKwh: Double? = null,
)

/**
 * Tagesverlauf in 15-Minuten-Slots (96 pro Tag), Werte in W, null = keine Daten.
 * @param date ISO-Datum (yyyy-MM-dd), damit gestrige Kurven nach Mitternacht nicht angezeigt werden.
 */
data class DayCurve(val date: String, val pv: List<Float?>, val load: List<Float?>) {
    companion object {
        const val SLOTS = 96
        val EMPTY = DayCurve("", List(SLOTS) { null }, List(SLOTS) { null })
    }
}

/** Gesamter Cache-Inhalt, den Widget und App lesen. */
data class PvSnapshot(
    val values: PvValues?,
    val lastUpdatedEpochMs: Long?,
    val lastAttemptEpochMs: Long?,
    val status: SyncStatus,
    val message: String?,
    val syncing: Boolean,
    val curve: DayCurve = DayCurve.EMPTY,
) {
    /** Auch ohne Fehler gilt ein Stand als veraltet, wenn er zu alt ist. */
    fun isOutdated(now: Long = System.currentTimeMillis()): Boolean =
        status != SyncStatus.OK || lastUpdatedEpochMs == null || now - lastUpdatedEpochMs > MAX_AGE_MS

    companion object {
        const val MAX_AGE_MS = 90 * 60 * 1000L
        val EMPTY = PvSnapshot(null, null, null, SyncStatus.ERROR, "Noch keine Daten", false)
    }
}
