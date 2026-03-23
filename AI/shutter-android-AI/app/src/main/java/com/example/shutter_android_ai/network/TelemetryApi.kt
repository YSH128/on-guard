package com.example.shutter_android_ai.network

import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

data class TelemetryRequest(
    val user_id: Int,
    val model_type: String,
    val model_version: String,
    val inference_success: Boolean,
    val failure_type: String? = null,
    val inference_latency_ms: Long,
    val risk_score: Float,
    val timestamp: String,
    val device_hash: String,
    val session_id: String,
    val device_temperature: Float,
    val app_pss_memory: Int,
    val thermal_throttling_status: Int,
    val thread_cpu_time: Long,
    val battery_current_now: Float,
    val model_load_latency_ms: Long
)

interface TelemetryApi {
    @POST("telemetry")
    suspend fun sendTelemetry(@Body request: TelemetryRequest): Response<Unit>
}

object RetrofitClient {
    private const val BASE_URL = "http://172.20.10.3:8080/"

    val api: TelemetryApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TelemetryApi::class.java)
    }
}
