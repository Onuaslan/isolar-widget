package de.isolarwidget.data.api

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    private val okHttp: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                // Nie BODY loggen: Tokens und Secret Key stehen in Header/Body.
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()
    }

    private val cache = mutableMapOf<Region, IsolarApi>()

    @Synchronized
    fun forRegion(region: Region): IsolarApi = cache.getOrPut(region) {
        Retrofit.Builder()
            .baseUrl(region.gatewayUrl)
            .client(okHttp)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(IsolarApi::class.java)
    }
}
