package de.isolarwidget.data.auth

import de.isolarwidget.data.api.ApiClient
import de.isolarwidget.data.api.ApiException
import de.isolarwidget.data.api.AuthException
import de.isolarwidget.data.api.str
import de.isolarwidget.data.api.unwrap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * Hält einen gültigen Access-Token bereit. Refresh ist per Mutex serialisiert,
 * damit Widget-Refresh und periodischer Job nicht gleichzeitig refreshen
 * (ein Refresh-Token ist danach evtl. ungültig).
 */
class TokenManager(private val store: CredentialStore) {

    private val mutex = Mutex()

    suspend fun exchangeCode(code: String) = mutex.withLock {
        val c = store.credentials
        val body = buildJsonObject {
            put("appkey", c.appKey)
            put("code", code)
            put("grant_type", "authorization_code")
            put("redirect_uri", OAuth.REDIRECT_URI)
        }
        store.tokens = parseTokens(ApiClient.forRegion(c.region).exchangeCode(c.secretKey, body).unwrap())
    }

    suspend fun validAccessToken(): String = mutex.withLock {
        val t = store.tokens ?: throw AuthException("Nicht angemeldet")
        if (System.currentTimeMillis() < t.expiresAtEpochMs - SAFETY_MS) t.accessToken else refreshLocked(t).accessToken
    }

    /** Nach 401/403 bzw. Token-Fehler: erzwingt einen Refresh, außer ein anderer Aufruf hat schon refresht. */
    suspend fun forceRefresh(rejectedToken: String): String = mutex.withLock {
        val t = store.tokens ?: throw AuthException("Nicht angemeldet")
        if (t.accessToken != rejectedToken) t.accessToken else refreshLocked(t).accessToken
    }

    private suspend fun refreshLocked(t: Tokens): Tokens {
        val c = store.credentials
        val body = buildJsonObject {
            put("appkey", c.appKey)
            put("refresh_token", t.refreshToken)
        }
        val fresh = try {
            parseTokens(ApiClient.forRegion(c.region).refreshToken(c.secretKey, body).unwrap())
        } catch (e: AuthException) {
            // Refresh-Token abgelaufen/widerrufen: nur erneute Freigabe im Browser hilft.
            store.tokens = null
            throw AuthException("Anmeldung abgelaufen, bitte in der App neu verbinden")
        }
        store.tokens = fresh
        return fresh
    }

    /** Token-Felder stehen je nach Server oben oder in result_data. */
    private fun parseTokens(resp: JsonObject): Tokens {
        val src = (resp["result_data"] as? JsonObject)?.takeIf { it.containsKey("access_token") } ?: resp
        val access = src.str("access_token") ?: throw ApiException("access_token fehlt")
        val refresh = src.str("refresh_token") ?: throw ApiException("refresh_token fehlt")
        val expiresIn = (src["expires_in"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull() ?: 3600L
        return Tokens(access, refresh, System.currentTimeMillis() + expiresIn * 1000)
    }

    private companion object { const val SAFETY_MS = 60_000L }
}
