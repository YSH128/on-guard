package com.s14p21a404.shutter.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

// 1. 데이터 클래스 (새로운 지표 모두 포함)
data class TelemetryRequest(
    val user_id: Long,
    val model_type: String,
    val model_version: String,
    val inference_success: Boolean,
    val failure_type: String? = null,
    val inference_latency_ms: Long,
    val risk_score: Float, 
    val timestamp: String,
    val device_hash: String,        // SharedPreferences에 저장된 UUID
    // val device_model: String,       // DeviceStatusProvider.getDeviceModelName()
    val session_id: String,
    val device_temperature: Float,
    val app_pss_memory: Int,
    val thermal_throttling_status: Int,
    val thread_cpu_time: Long,
    val battery_current_now: Float,
    val model_load_latency_ms: Long
)

// 2. API 인터페이스
interface TelemetryApi {
    @POST("telemetry")
    suspend fun sendTelemetry(@Body request: TelemetryRequest): Response<Unit>
}
