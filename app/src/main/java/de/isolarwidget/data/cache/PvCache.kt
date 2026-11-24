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

/** Single Source of Truth für das Widget. Nur der Sync schreibt Werte, das Widget liest nur. */
class PvCache(context: Context) {
    private val store = context.applicationContext.pvStore

    val snapshot: Flow<PvSnapshot> = store.data.map { it.toSnapshot() }

    suspend fun current(): PvSnapshot = snapshot.first()

    suspend fun saveSuccess(values: PvValues, now: Long = System.currentTimeMillis()) = store.edit {
        it.putOrRemove(PV, values.pvPowerW)
        it.putOrRemove(SOC, values.batterySocPct)
        it.putOrRemove(GRID, values.gridPowerW)
        it.putOrRemove(YIELD, values.dailyYieldKwh)
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
        values = if (this[HAS_VALUES] == true) PvValues(this[PV], this[SOC], this[GRID], this[YIELD]) else null,
        lastUpdatedEpochMs = this[UPDATED],
        lastAttemptEpochMs = this[ATTEMPT],
        status = this[STATUS]?.let { s -> SyncStatus.entries.firstOrNull { it.name == s } } ?: SyncStatus.ERROR,
        message = this[MESSAGE] ?: if (this[HAS_VALUES] != true) "Noch keine Daten" else null,
        syncing = this[SYNCING] == true,
    )

    private companion object {
        val PV = doublePreferencesKey("pv_power_w")
        val SOC = doublePreferencesKey("battery_soc_pct")
        val GRID = doublePreferencesKey("grid_power_w")
        val YIELD = doublePreferencesKey("daily_yield_kwh")
        val HAS_VALUES = booleanPreferencesKey("has_values")
        val UPDATED = longPreferencesKey("last_updated_epoch_ms")
        val ATTEMPT = longPreferencesKey("last_attempt_epoch_ms")
        val STATUS = stringPreferencesKey("last_sync_status")
        val MESSAGE = stringPreferencesKey("status_message")
        val SYNCING = booleanPreferencesKey("syncing")
    }
}
