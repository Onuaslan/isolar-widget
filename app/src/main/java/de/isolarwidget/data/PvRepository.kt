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
                RealtimeParser.parse(data, plantId, store.invertGridSign)
            }
        }
        result.onSuccess { cache.saveSuccess(it) }
            .onFailure { cache.saveFailure(describe(it)) }
        return result
    }

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
            }
        }.getOrElse { describe(it) }
    }

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
