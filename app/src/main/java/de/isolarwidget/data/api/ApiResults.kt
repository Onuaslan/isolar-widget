package de.isolarwidget.data.api

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import retrofit2.Response

/** Session ungültig, Token abgelaufen oder Berechtigung entzogen. */
class AuthException(message: String) : Exception(message)

/** Fachlicher Fehler der API (result_code != "1") oder unerwartetes Format. */
class ApiException(message: String) : Exception(message)

internal fun JsonObject.str(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull

/**
 * Prüft HTTP-Status und die iSolarCloud-Hülle {result_code, result_msg, result_data}.
 * Gibt die komplette Antwort zurück (manche Endpunkte, z. B. /token, liefern Felder auf oberster Ebene).
 */
internal fun Response<JsonObject>.unwrap(): JsonObject {
    if (code() == 401 || code() == 403) throw AuthException("HTTP ${code()}")
    if (!isSuccessful) throw ApiException("HTTP ${code()}")
    val body = body() ?: throw ApiException("Leere Antwort")

    body.str("error")?.let { err ->
        val desc = body.str("error_description") ?: err
        if (looksLikeAuthError(err, desc)) throw AuthException(desc) else throw ApiException(desc)
    }
    val code = body.str("result_code")
    if (code != null && code != "1") {
        val msg = body.str("result_msg") ?: "Fehlercode $code"
        if (looksLikeAuthError(code, msg)) throw AuthException(msg) else throw ApiException(msg)
    }
    return body
}

private fun looksLikeAuthError(code: String, msg: String): Boolean {
    val s = "$code $msg".lowercase()
    return listOf("token", "auth", "unauthor", "expired", "er_token", "login").any { it in s }
}

internal fun JsonObject.resultData(): JsonObject =
    (this["result_data"] as? JsonObject) ?: throw ApiException("result_data fehlt")
