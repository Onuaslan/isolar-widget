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

    /** Alle bekannten Anlagen-Messpunkte (pysolarcloud), nur für die Diagnose. */
    val DIAGNOSTIC_POINTS = listOf("83022","83024","83033","83019","83006","83020","83011","83021","83032","83007","83002","83009","83004","83012","83013","83023","83005","83025","83018","83001","83008","83010","83016","83017","83046","83052","83067","83097","83100","83102","83105","83106","83118","83124","83119","83072","83075","83252","83129","83232","83233","83234","83235","83236","83237","83238","83239","83240","83243","83241","83244","83242","83548","83549","83419","83317","83318","83319","83320","83321","83322","83324","83323","83325","83327","83326","83328","83329","83330","83331","83332","83334","83335")

    /**
     * Geräteebene (Energiespeichersystem/Hybrid-WR). Manche Anlagen liefern Netzleistung nur hier.
     * 13149 = Netzbezug (W), 13121 = Einspeiseleistung (W), 13126/13150 = Batterie laden/entladen (W).
     */
    const val DEV_PURCHASED = "13149"
    const val DEV_EXPORT = "13121"
    val DEVICE_POINTS = listOf("13003", "13011", "13119", DEV_EXPORT, "13126", "13141", DEV_PURCHASED, "13150")

    /** Netz aus Gerätedaten: Bezug − Einspeisung (+ Bezug / − Einspeisung). */
    fun parseDeviceGrid(resultData: JsonObject): Double? {
        val p = deviceMap(resultData) ?: return null
        val buy = p.num("p$DEV_PURCHASED")
        val sell = p.num("p$DEV_EXPORT")
        if (buy == null && sell == null) return null
        return (buy ?: 0.0) - (sell ?: 0.0)
    }

    fun deviceMap(resultData: JsonObject): JsonObject? =
        (resultData["device_point_list"] as? JsonArray)?.firstNotNullOfOrNull { it.asPointMap() }

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

    internal fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    internal fun JsonObject.num(key: String): Double? =
        str(key)?.trim()?.takeIf { it.isNotEmpty() && it != "--" }?.replace(',', '.')?.toDoubleOrNull()
}
