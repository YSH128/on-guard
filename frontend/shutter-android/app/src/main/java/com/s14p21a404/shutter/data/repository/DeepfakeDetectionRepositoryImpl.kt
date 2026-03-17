package com.s14p21a404.shutter.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.media.projection.MediaProjection
import android.util.Log
import com.s14p21a404.shutter.data.capture.ScreenCaptureManager
import com.s14p21a404.shutter.data.detector.DeepfakeDetector
import com.s14p21a404.shutter.domain.model.DeepfakeDetectionResult
import com.s14p21a404.shutter.domain.repository.DeepfakeDetectionRepository
import com.s14p21a404.shutter.network.RetrofitClient
import com.s14p21a404.shutter.network.TelemetryRequest
import com.s14p21a404.shutter.telemetry.DeviceStatusProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import android.os.Build
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID

/**
 * 딥페이크 감지 Repository 구현체
 *
 * DeepfakeDetector 인터페이스를 통해 실제 감지를 수행하고,
 * 결과를 SharedFlow로 방출한다.
 *
 * 화면 캡처(ScreenCaptureManager)는 startDetection/stopDetection 내부에서
 * 자동으로 관리되므로, 외부에서 별도로 캡처를 제어할 필요 없다.
 */
class DeepfakeDetectionRepositoryImpl(
    private val detector: DeepfakeDetector,
    private val telemetryApi: com.s14p21a404.shutter.network.TelemetryApi,
    private val tokenManager: com.s14p21a404.shutter.data.token.TokenManager,
) : DeepfakeDetectionRepository {

    private val _results = MutableSharedFlow<DeepfakeDetectionResult>(
        replay = 0,
        extraBufferCapacity = 1
    )

    // ── 화면 캡처 내부 관리 ──
    private var captureContext: Context? = null
    private var mediaProjection: MediaProjection? = null
    private var screenCaptureManager: ScreenCaptureManager? = null

    override fun observeDetectionResults(): Flow<DeepfakeDetectionResult> = _results.asSharedFlow()

    override fun startDetection() {
        Log.d(TAG, "감지 시작")
        detector.startDetection { result ->
            Log.d(TAG, "📊 감지 결과: $result")
            _results.tryEmit(result)
            
            // 텔레메트리 비동기 전송
            sendTelemetry(result)
        }

        // ── 화면 캡처 시작 ──
        val ctx = captureContext
        val projection = mediaProjection
        if (ctx == null || projection == null) {
            Log.w(TAG, "MediaProjection 없음 — 화면 캡처 불가 (감지는 Stub으로 동작)")
            return
        }

        screenCaptureManager = ScreenCaptureManager(
            context = ctx,
            mediaProjection = projection,
            detectors = listOf(detector)
        )
        screenCaptureManager?.startCapture()
    }

    override fun stopDetection() {
        Log.d(TAG, "감지 중단")

        // ── 화면 캡처 중단 ──
        screenCaptureManager?.stopCapture()
        screenCaptureManager = null

        detector.stopDetection()
    }

    override fun setMediaProjection(context: Context, mediaProjection: MediaProjection) {
        this.captureContext = context
        this.mediaProjection = mediaProjection
        Log.d(TAG, "MediaProjection 설정 완료")
    }

    override fun getLatestBitmap(): Bitmap? = screenCaptureManager?.latestBitmap

    override fun releaseMediaProjection() {
        screenCaptureManager?.stopCapture()
        screenCaptureManager = null
        mediaProjection?.stop()
        mediaProjection = null
        captureContext = null
        Log.d(TAG, "MediaProjection 해제 완료")
    }

    private fun sendTelemetry(result: DeepfakeDetectionResult) {
        val ctx = captureContext ?: return
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 저장된 가상의 UUID 발급 (프로덕션에서는 SharedPreferences 사용 권장)
                val dummyDeviceHash = "android-device-" + Build.MODEL
                // JSON에서 상세 모니터링 수치 파싱
                var errorType: String? = null
                var inferenceLatency = 0L
                var loadLatency = 0L
                var temp = 0f
                var ram = 0
                var cpuTime = 0L
                var battery = 0f
                var thermal = 0
                var riskScore = 0f
                
                val rawJson = result.rawResultJson
                if (rawJson != null && rawJson.startsWith("{")) {
                    try {
                        val obj = JSONObject(rawJson)
                        if (obj.has("error")) {
                            errorType = obj.getString("error")
                        } else if (obj.has("debug_info")) {
                            val debugInfo = obj.getString("debug_info")
                            // Fake: 85.00% 구문 찾기
                            val fakeMatch = Regex("Fake: ([0-9.]+)%").find(debugInfo)
                            if (fakeMatch != null) {
                                riskScore = fakeMatch.groupValues[1].toFloat() / 100f
                            }
                            
                            val loadMatch = Regex("Load: ([0-9.]+) ms").find(debugInfo)
                            if (loadMatch != null) loadLatency = loadMatch.groupValues[1].toFloat().toLong()
                            
                            val inferMatch = Regex("Infer: ([0-9.]+) ms").find(debugInfo)
                            if (inferMatch != null) inferenceLatency = inferMatch.groupValues[1].toFloat().toLong()
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "텔레메트리 파싱 에러", e)
                    }
                }
                
                // 직접 조회 가능한 하드웨어 상태
                if (temp == 0f) temp = DeviceStatusProvider.getDeviceTemperature(ctx)
                if (ram == 0) ram = DeviceStatusProvider.getAppPssMemory()
                if (thermal == 0) thermal = DeviceStatusProvider.getThermalThrottlingStatus(ctx)
                if (battery == 0f) battery = DeviceStatusProvider.getBatteryCurrentNow(ctx)

                val request = TelemetryRequest(
                    user_id = tokenManager.getUserId().toInt(),
                    model_type = "FACE",
                    model_version = "v1",
                    inference_success = errorType == null,
                    failure_type = errorType,
                    inference_latency_ms = inferenceLatency,
                    risk_score = riskScore.toDouble(),
                    timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(System.currentTimeMillis()),
                    device_hash = dummyDeviceHash,
                    session_id = UUID.randomUUID().toString(),
                    device_temperature = temp,
                    app_pss_memory = ram,
                    thermal_throttling_status = thermal,
                    thread_cpu_time = cpuTime,
                    battery_current_now = battery,
                    model_load_latency_ms = loadLatency.toInt()
                )
                
                Log.d(TAG, "[텔레메트리] 전송 요청 데이터: $request")
                val response = telemetryApi.sendTelemetry(request)
                if (response.isSuccessful) {
                    Log.d(TAG, "텔레메트리 전송 성공")
                } else {
                    Log.w(TAG, "텔레메트리 전송 실패: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "텔레메트리 전송 중 예외 발생", e)
            }
        }
    }

    companion object {
        private const val TAG = "DeepfakeDetectionRepo"
    }
}
