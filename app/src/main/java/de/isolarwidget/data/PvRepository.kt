package de.isolarwidget.data

import android.content.Context
import android.net.Uri
import de.isolarwidget.data.api.ApiClient
import de.isolarwidget.data.api.ApiException
import de.isolarwidget.data.api.AuthException
import de.isolarwidget.data.api.IsolarApi
import de.isolarwidget.data.api.resultData
import de.isolarwidget.data.api.str
import de.isolarwidget.data.api.unwrap
import de.isolarwidget.data.auth.AppCredentials
import de.isolarwidget.data.auth.CredentialStore
import de.isolarwidget.data.auth.OAuth
import de.isolarwidget.data.auth.TokenManager
import de.isolarwidget.data.cache.DayCurve
import de.isolarwidget.data.cache.PvCache
import de.isolarwidget.data.cache.PvValues
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.IOException
import java.util.UUID

data class Plant(val id: String, val name: String)

class PvRepository private constructor(context: Context) {

    val store = CredentialStore(context)
    val cache = PvCache(context)
    private val tokens = TokenManager(store)

    val isLoggedIn get() = store.tokens != null
    val isConfigured get() = isLoggedIn && store.plantId != null

    // ---------- Login (OAuth2 Authorization Code) ----------

    fun startLogin(credentials: AppCredentials): String {
        store.credentials = credentials
        val state = UUID.randomUUID().toString()
        store.pendingOAuthState = state
        return OAuth.authorizeUrl(credentials.region, credentials.appId, state)
    }

    /** @return null bei Erfolg, sonst Fehlermeldung für die UI. */
    suspend fun completeLogin(redirect: Uri): String? {
        val cb = OAuth.parseCallback(redirect) ?: return "Kein Autorisierungscode in der URL"
        val expected = store.pendingOAuthState
        // state wird geprüft, wenn der Server ihn zurückgibt (CSRF-Schutz).
        if (cb.state != null && expected != null && cb.state != expected) return "Ungültiger state-Parameter"
        return runCatching { tokens.exchangeCode(cb.code) }
            .onSuccess { store.pendingOAuthState = null }
            .exceptionOrNull()?.let(::describe)
    }

    fun logout() {
        store.tokens = null
        store.plantId = null
        store.plantName = null
    }

    // ---------- Anlagen ----------

    suspend fun loadPlants(): List<Plant> = authed { api, bearer ->
        val data = api.plantList(store.credentials.secretKey, bearer, body {
            put("page", 1); put("size", 100)
        }).unwrap().resultData()
        (data["pageList"] as? JsonArray).orEmpty().mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val id = o.str("ps_id") ?: return@mapNotNull null
            Plant(id, o.str("ps_name") ?: "Anlage $id")
        }
    }

    fun selectPlant(plant: Plant) {
        store.deviceKey = null
        store.plantId = plant.id
        store.plantName = plant.name
    }

    // ---------- Sync ----------

    /** Holt Werte und schreibt den Cache. Wirft nie; Fehler landen als STALE/ERROR im Cache. */
    suspend fun sync(): Result<PvValues> {
        val plantId = store.plantId
        val result = if (plantId == null) {
            Result.failure(IllegalStateException("Keine Anlage ausgewählt"))
        } else runCatching {
            authed { api, bearer ->
                val data = api.realtimeData(store.credentials.secretKey, bearer, body {
                    putJsonArray("ps_id_list") { add(JsonPrimitive(plantId)) }
                    putJsonArray("point_id_list") {
                        RealtimeParser.ALL_POINTS.forEach { add(JsonPrimitive(it)) }
                    }
                    put("is_get_point_dict", "0")
                }).unwrap().resultData()
                val values = RealtimeParser.parse(data, plantId, false)
                val (devGrid, devBatt) = deviceData(api, bearer, plantId)
                val grid = (values.gridPowerW ?: devGrid)?.let { if (store.invertGridSign) -it else it }
                // Fallback Energiebilanz: PV + Netz = Haus + Batterie(laden)
                val batt = devBatt ?: run {
                    val pv = values.pvPowerW; val load = values.loadPowerW
                    if (grid != null && pv != null && load != null) pv + grid - load else null
                }
                values.copy(gridPowerW = grid, batteryPowerW = batt)
            }
        }
        val curve = if (result.isSuccess && plantId != null) loadCurve(plantId) else null
        result.onSuccess { cache.saveSuccess(it, curve) }
            .onFailure { cache.saveFailure(describe(it)) }
        return result
    }

    // ---------- Tageskurve ----------

    /**
     * Lädt PV-Leistung und Hauslast von 0 Uhr bis jetzt in 15-Minuten-Schritten.
     * Die API begrenzt evtl. den Zeitraum pro Anfrage, daher in 3-Stunden-Fenstern.
     * Fehler sind nicht fatal: dann bleibt die alte Kurve stehen (Rückgabe null).
     */
    private suspend fun loadCurve(plantId: String): DayCurve? = runCatching {
        val zone = java.time.ZoneId.systemDefault()
        val now = java.time.LocalDateTime.now(zone)
        val fmt = java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
        val pv = arrayOfNulls<Float>(DayCurve.SLOTS)
        val load = arrayOfNulls<Float>(DayCurve.SLOTS)
        var start = now.toLocalDate().atStartOfDay()
        authed { api, bearer ->
            while (start.isBefore(now)) {
                val end = minOf(start.plusHours(3), now)
                val data = api.minuteData(store.credentials.secretKey, bearer, body {
                    putJsonArray("ps_id_list") { add(JsonPrimitive(plantId)) }
                    put("points", "p${RealtimeParser.CURVE_PV},p${RealtimeParser.CURVE_LOAD}")
                    put("start_time_stamp", start.format(fmt))
                    put("end_time_stamp", end.format(fmt))
                    put("minute_interval", "15")
                    put("is_get_point_dict", "0")
                }).unwrap().resultData()
                RealtimeParser.parseCurveInto(data, pv, load)
                start = end
            }
        }
        DayCurve(now.toLocalDate().toString(), pv.toList(), load.toList())
    }.onFailure { android.util.Log.w("PvRepository", "Kurve nicht geladen", it) }.getOrNull()

    // ---------- Geräteebene (Netzleistung) ----------

    private data class Device(val psKey: String, val type: Int, val name: String)

    private suspend fun devices(api: IsolarApi, bearer: String, plantId: String): List<Device> {
        val data = api.deviceList(store.credentials.secretKey, bearer, body {
            put("ps_id", plantId); put("page", 1); put("size", 50)
        }).unwrap().resultData()
        return (data["pageList"] as? JsonArray).orEmpty().mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val key = o.str("ps_key") ?: return@mapNotNull null
            Device(key, o.str("device_type")?.toIntOrNull() ?: 0, o.str("device_name").orEmpty())
        }
    }

    /** Bevorzugt Energiespeichersystem (14), sonst Wechselrichter (1), sonst Zähler (7). */
    private fun pickDevice(list: List<Device>): Device? =
        listOf(14, 1, 7).firstNotNullOfOrNull { t -> list.firstOrNull { it.type == t } }

    private suspend fun deviceRealtime(api: IsolarApi, bearer: String, d: Device, points: List<String>, dict: Boolean) =
        api.deviceRealtimeData(store.credentials.secretKey, bearer, body {
            put("device_type", d.type)
            putJsonArray("ps_key_list") { add(JsonPrimitive(d.psKey)) }
            putJsonArray("point_id_list") { points.forEach { add(JsonPrimitive(it)) } }
            if (dict) put("is_get_point_dict", "1")
        }).unwrap().resultData()

    /** Netz- und Batterieleistung aus Gerätedaten; null-Werte, wenn nicht verfügbar. */
    private suspend fun deviceData(api: IsolarApi, bearer: String, plantId: String): Pair<Double?, Double?> = runCatching {
        val cachedKey = store.deviceKey
        val device = if (cachedKey != null) Device(cachedKey, store.deviceType, "")
        else pickDevice(devices(api, bearer, plantId))?.also { store.deviceKey = it.psKey; store.deviceType = it.type }
        device?.let { RealtimeParser.parseDevice(deviceRealtime(api, bearer, it, RealtimeParser.DEVICE_POINTS, false)) }
            ?: (null to null)
    }.onFailure { if (it is AuthException) throw it }.getOrElse { null to null }

    /**
     * Diagnose: fragt alle bekannten Messpunkte ab und liefert die, für die die Anlage Werte hat,
     * als lesbaren Text ("p83067 Name = 2450 W").
     */
    suspend fun diagnose(): String {
        val plantId = store.plantId ?: return "Keine Anlage ausgewählt"
        return runCatching {
            authed { api, bearer ->
                val data = api.realtimeData(store.credentials.secretKey, bearer, body {
                    putJsonArray("ps_id_list") { add(JsonPrimitive(plantId)) }
                    putJsonArray("point_id_list") { RealtimeParser.DIAGNOSTIC_POINTS.forEach { add(JsonPrimitive(it)) } }
                    put("is_get_point_dict", "1")
                }).unwrap().resultData()
                val dict = (data["point_dict"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
                    .associateBy { it.str("point_id").orEmpty() }
                val entry = (data["device_point_list"] as? JsonArray)?.firstOrNull() as? JsonObject
                    ?: return@authed "Keine Daten"
                val point = (entry["device_point"] as? JsonObject) ?: entry
                point.keys.filter { it.startsWith("p") && it.drop(1).all(Char::isDigit) }.sorted()
                    .mapNotNull { k ->
                        val v = point.str(k)?.takeIf { it.isNotBlank() && it != "--" } ?: return@mapNotNull null
                        val d = dict[k.drop(1)]
                        "$k ${d?.str("point_name").orEmpty()} = $v ${d?.str("point_unit").orEmpty()}".trim()
                    }.joinToString("\n").ifEmpty { "Keine Messpunkte mit Werten" }
                    .plus("\n\n" + diagnoseDevices(api, bearer, plantId))
            }
        }.getOrElse { describe(it) }
    }

    private suspend fun diagnoseDevices(api: IsolarApi, bearer: String, plantId: String): String = runCatching {
        val list = devices(api, bearer, plantId)
        val header = "Geräte:\n" + list.joinToString("\n") { "typ ${it.type} ${it.name} (${it.psKey})" }
        val d = pickDevice(list) ?: return@runCatching header
        val data = deviceRealtime(api, bearer, d, RealtimeParser.DEVICE_POINTS, true)
        val point = RealtimeParser.deviceMap(data)
        val values = point?.keys?.filter { it.startsWith("p") && it.drop(1).all(Char::isDigit) }?.sorted()
            ?.joinToString("\n") { "$it = ${point.str(it)}" }.orEmpty()
        "$header\n\nGerät typ ${d.type}:\n$values"
    }.getOrElse { "Geräte: ${describe(it)}" }

    /** Führt einen Aufruf aus; bei Auth-Fehler genau einmal Token erneuern und wiederholen. */
    private suspend fun <T> authed(block: suspend (IsolarApi, String) -> T): T {
        val api = ApiClient.forRegion(store.credentials.region)
        val token = tokens.validAccessToken()
        return try {
            block(api, "Bearer $token")
        } catch (e: AuthException) {
            val fresh = tokens.forceRefresh(token)
            block(api, "Bearer $fresh")
        }
    }

    private fun body(extra: JsonObjectBuilder.() -> Unit): JsonObject = buildJsonObject {
        put("appkey", store.credentials.appKey)
        put("lang", "_de_DE")
        extra()
    }

    private fun describe(t: Throwable): String = when (t) {
        is AuthException -> "Anmeldung: ${t.message}"
        is IOException -> "Keine Verbindung"
        is SerializationException -> "Unerwartete Antwort"
        is ApiException -> "API: ${t.message}"
        else -> t.message ?: t.javaClass.simpleName
    }

    companion object {
        @Volatile private var instance: PvRepository? = null
        fun get(context: Context): PvRepository =
            instance ?: synchronized(this) {
                instance ?: PvRepository(context.applicationContext).also { instance = it }
            }
    }
}
