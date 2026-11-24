package de.isolarwidget.data.api

import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * iSolarCloud Open API (OAuth2-Variante). Alle Aufrufe sind POST mit JSON-Body.
 * Pflicht: Header "x-access-key" (= Secret Key aus dem Developer-Portal),
 * Body-Felder "appkey" und "lang"; für Datenendpunkte zusätzlich "Authorization: Bearer <token>".
 * Antworten werden bewusst als JsonObject gelesen, damit fehlende/unerwartete Felder nicht crashen.
 */
interface IsolarApi {

    @POST("openapi/apiManage/token")
    suspend fun exchangeCode(
        @Header("x-access-key") accessKey: String,
        @Body body: JsonObject,
    ): Response<JsonObject>

    @POST("openapi/apiManage/refreshToken")
    suspend fun refreshToken(
        @Header("x-access-key") accessKey: String,
        @Body body: JsonObject,
    ): Response<JsonObject>

    @POST("openapi/platform/queryPowerStationList")
    suspend fun plantList(
        @Header("x-access-key") accessKey: String,
        @Header("Authorization") bearer: String,
        @Body body: JsonObject,
    ): Response<JsonObject>

    @POST("openapi/platform/getPowerStationRealTimeData")
    suspend fun realtimeData(
        @Header("x-access-key") accessKey: String,
        @Header("Authorization") bearer: String,
        @Body body: JsonObject,
    ): Response<JsonObject>

    @POST("openapi/platform/getDeviceListByPsId")
    suspend fun deviceList(
        @Header("x-access-key") accessKey: String,
        @Header("Authorization") bearer: String,
        @Body body: JsonObject,
    ): Response<JsonObject>

    @POST("openapi/platform/getDeviceRealTimeData")
    suspend fun deviceRealtimeData(
        @Header("x-access-key") accessKey: String,
        @Header("Authorization") bearer: String,
        @Body body: JsonObject,
    ): Response<JsonObject>
}
