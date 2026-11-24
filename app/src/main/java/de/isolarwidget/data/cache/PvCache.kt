package de.isolarwidget.data.cache

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.pvStore: DataStore<Preferences> by preferencesDataStore(name = "pv_cache")

/** Single Source of Truth für die Widgets. Nur der Sync schreibt Werte, die Widgets lesen nur. */
class PvCache(context: Context) {
    private val store = context.applicationContext.pvStore

    val snapshot: Flow<PvSnapshot> = store.data.map { it.toSnapshot() }

    suspend fun current(): PvSnapshot = snapshot.first()

    suspend fun saveSuccess(values: PvValues, curve: DayCurve?, now: Long = System.currentTimeMillis()) = store.edit {
        it.putOrRemove(PV, values.pvPowerW)
        it.putOrRemove(SOC, values.batterySocPct)
        it.putOrRemove(GRID, values.gridPowerW)
        it.putOrRemove(YIELD, values.dailyYieldKwh)
        it.putOrRemove(LOAD, values.loadPowerW)
        it.putOrRemove(BATT_POWER, values.batteryPowerW)
        it.putOrRemove(CONSUMPTION, values.consumptionTodayKwh)
        it.putOrRemove(BUY, values.gridBuyTodayKwh)
        it.putOrRemove(FEED, values.feedInTodayKwh)
        it.putOrRemove(CHARGE, values.batteryChargeTodayKwh)
        it.putOrRemove(DISCHARGE, values.batteryDischargeTodayKwh)
        // Kurve nur ersetzen, wenn neu geladen; ein fehlgeschlagener Kurvenabruf behält die alte.
        if (curve != null) it[CURVE] = encode(curve)
        it[HAS_VALUES] = true
        it[UPDATED] = now
        it[ATTEMPT] = now
        it[STATUS] = SyncStatus.OK.name
        it.remove(MESSAGE)
        it[SYNCING] = false
    }

    /** Fehlschlag: letzte gültige Werte bleiben erhalten und werden als STALE markiert. */
    suspend fun saveFailure(message: String, now: Long = System.currentTimeMillis()) = store.edit {
        val hasValues = it[HAS_VALUES] == true
        it[STATUS] = (if (hasValues) SyncStatus.STALE else SyncStatus.ERROR).name
        it[MESSAGE] = message.take(120)
        it[ATTEMPT] = now
        it[SYNCING] = false
    }

    suspend fun setSyncing(value: Boolean) = store.edit { it[SYNCING] = value }

    suspend fun clear() = store.edit { it.clear() }

    private fun MutablePreferences.putOrRemove(key: Preferences.Key<Double>, v: Double?) {
        if (v == null) remove(key) else this[key] = v
    }

    private fun Preferences.toSnapshot() = PvSnapshot(
        values = if (this[HAS_VALUES] == true) PvValues(
            pvPowerW = this[PV], batterySocPct = this[SOC], gridPowerW = this[GRID], dailyYieldKwh = this[YIELD],
            loadPowerW = this[LOAD], batteryPowerW = this[BATT_POWER], consumptionTodayKwh = this[CONSUMPTION], gridBuyTodayKwh = this[BUY],
            feedInTodayKwh = this[FEED], batteryChargeTodayKwh = this[CHARGE], batteryDischargeTodayKwh = this[DISCHARGE],
        ) else null,
        lastUpdatedEpochMs = this[UPDATED],
        lastAttemptEpochMs = this[ATTEMPT],
        status = this[STATUS]?.let { s -> SyncStatus.entries.firstOrNull { it.name == s } } ?: SyncStatus.ERROR,
        message = this[MESSAGE] ?: if (this[HAS_VALUES] != true) "Noch keine Daten" else null,
        syncing = this[SYNCING] == true,
        curve = this[CURVE]?.let(::decode) ?: DayCurve.EMPTY,
    )

    companion object {
        private val PV = doublePreferencesKey("pv_power_w")
        private val SOC = doublePreferencesKey("battery_soc_pct")
        private val GRID = doublePreferencesKey("grid_power_w")
        private val YIELD = doublePreferencesKey("daily_yield_kwh")
        private val LOAD = doublePreferencesKey("load_power_w")
        private val BATT_POWER = doublePreferencesKey("battery_power_w")
        private val CONSUMPTION = doublePreferencesKey("consumption_today_kwh")
        private val BUY = doublePreferencesKey("grid_buy_today_kwh")
        private val FEED = doublePreferencesKey("feed_in_today_kwh")
        private val CHARGE = doublePreferencesKey("battery_charge_today_kwh")
        private val DISCHARGE = doublePreferencesKey("battery_discharge_today_kwh")
        private val CURVE = stringPreferencesKey("day_curve")
        private val HAS_VALUES = booleanPreferencesKey("has_values")
        private val UPDATED = longPreferencesKey("last_updated_epoch_ms")
        private val ATTEMPT = longPreferencesKey("last_attempt_epoch_ms")
        private val STATUS = stringPreferencesKey("last_sync_status")
        private val MESSAGE = stringPreferencesKey("status_message")
        private val SYNCING = booleanPreferencesKey("syncing")

        /** "2026-11-24|1.0,,3.5,…|…" – leere Felder = keine Daten. */
        fun encode(c: DayCurve): String =
            c.date + "|" + c.pv.joinToString(",") { it?.toString().orEmpty() } +
                "|" + c.load.joinToString(",") { it?.toString().orEmpty() }

        fun decode(s: String): DayCurve? {
            val parts = s.split("|")
            if (parts.size != 3) return null
            fun list(p: String) = p.split(",").map { it.toFloatOrNull() }
                .let { l -> List(DayCurve.SLOTS) { i -> l.getOrNull(i) } }
            return DayCurve(parts[0], list(parts[1]), list(parts[2]))
        }
    }
}
