package com.example.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class ErApiResponse(
    @Json(name = "result") val result: String,
    @Json(name = "rates") val rates: Map<String, Double>
)

@JsonClass(generateAdapter = true)
data class CoinbaseResponse(
    @Json(name = "data") val data: CoinbaseData
)

@JsonClass(generateAdapter = true)
data class CoinbaseData(
    @Json(name = "amount") val amount: String,
    @Json(name = "currency") val currency: String
)

interface FxApiService {
    @GET("v6/latest/USD")
    suspend fun getUsdRates(): ErApiResponse
}

interface CoinbaseApiService {
    @GET("v2/prices/{pair}/spot")
    suspend fun getSpotPrice(@Path("pair") pair: String): CoinbaseResponse
}

object MarketDataClient {
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val moshi = com.squareup.moshi.Moshi.Builder()
        .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
        .build()

    val fxService: FxApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://open.er-api.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(FxApiService::class.java)
    }

    val coinbaseService: CoinbaseApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.coinbase.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(CoinbaseApiService::class.java)
    }
}
