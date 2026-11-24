package de.isolarwidget.data

import de.isolarwidget.data.api.ApiException
import de.isolarwidget.data.cache.PvValues
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Wandelt result_data von getPowerStationRealTimeData in PvValues.
 * Messpunkt-IDs (Anlagenebene) laut pysolarcloud; welche geliefert werden, hängt vom Wechselrichter ab,
 * daher je Kennzahl eine Fallback-Kette.
 */
object RealtimeParser {

    val PV_POWER = listOf("83067", "83033", "83329")      // W: PV-Gesamtleistung, Anlagenleistung, PV (EMS)
    val BATTERY_SOC = listOf("83252", "83129", "83334")   // Anteil 0..1 oder %
    val GRID_POWER = listOf("83549", "83328")             // W: Netz-Wirkleistung
    val DAILY_YIELD = listOf("83022", "83331")            // Wh: Tagesertrag

    /** Alle Punkte, die angefragt werden. */
    val ALL_POINTS: List<String> = (PV_POWER + BATTERY_SOC + GRID_POWER + DAILY_YIELD).distinct()

    /**
     * @param invertGridSign true negiert den Netzwert. Ziel-Konvention: + Bezug / − Einspeisung.
     *  Welches Vorzeichen die API liefert, ist nicht offiziell belegt und je Anlage zu prüfen (README).
     */
    fun parse(resultData: JsonObject, plantId: String, invertGridSign: Boolean): PvValues {
        val list = resultData["device_point_list"] as? JsonArray
            ?: throw ApiException("device_point_list fehlt")
        val plant = list.mapNotNull { it.asPointMap() }
            .firstOrNull { it.str("ps_id") == plantId }
            ?: list.firstNotNullOfOrNull { it.asPointMap() }
            ?: throw ApiException("Keine Daten für Anlage $plantId")

        fun first(ids: List<String>): Double? = ids.firstNotNullOfOrNull { plant.num("p$it") }

        val soc = first(BATTERY_SOC)?.let { if (it <= 1.0) it * 100.0 else it }?.coerceIn(0.0, 100.0)
        val grid = first(GRID_POWER)?.let { if (invertGridSign) -it else it }

        return PvValues(
            pvPowerW = first(PV_POWER),
            batterySocPct = soc,
            gridPowerW = grid,
            dailyYieldKwh = first(DAILY_YIELD)?.div(1000.0),
        )
    }

    /** Einträge sind entweder flach {ps_id, p83022, ...} oder unter "device_point" verschachtelt. */
    private fun JsonElement.asPointMap(): JsonObject? {
        val o = this as? JsonObject ?: return null
        return (o["device_point"] as? JsonObject) ?: o
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.num(key: String): Double? =
        str(key)?.trim()?.takeIf { it.isNotEmpty() && it != "--" }?.replace(',', '.')?.toDoubleOrNull()
}
