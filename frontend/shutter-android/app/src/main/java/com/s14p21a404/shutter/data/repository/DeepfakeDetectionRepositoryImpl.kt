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
import com.s14p21a404.shutter.data.detector.real.OpenCvFaceTracker
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
    private val faceTracker: OpenCvFaceTracker,
    private val telemetryApi: com.s14p21a404.shutter.network.TelemetryApi,
    private val tokenManager: com.s14p21a404.shutter.data.token.TokenManager,
) : DeepfakeDetectionRepository {

    private val _results = MutableSharedFlow<DeepfakeDetectionResult>(
        replay = 0,
        extraBufferCapacity = 1
    )

    private val _frameResults = MutableSharedFlow<DeepfakeDetectionResult>(
        replay = 0,
        extraBufferCapacity = 8
    )

    // ── 화면 캡처 내부 관리 ──
    private var captureContext: Context? = null
    private var mediaProjection: MediaProjection? = null
    private var screenCaptureManager: ScreenCaptureManager? = null

    /** onReady 콜백이 이미 발화했는지 (= 모델이 준비된 상태) */
    @Volatile
    private var isModelReady = false

    override fun setFaceTrackingEnabled(enabled: Boolean) {
        faceTracker.isTrackingEnabled = enabled
        Log.d(TAG, "OpenCV Face Tracking: $enabled")
    }

    override fun observeDetectionResults(): Flow<DeepfakeDetectionResult> = _results.asSharedFlow()

    override fun observeFrameResults(): Flow<DeepfakeDetectionResult> = _frameResults.asSharedFlow()

    override fun startDetection() {
        Log.d(TAG, "감지 및 모델 로드 시작")
        isModelReady = false
        
        detector.prepare()
        faceTracker.prepare()
        
        detector.startDetection(
            onResult = { result ->
                Log.d(TAG, "📊 감지 결과: $result")
                _results.tryEmit(result)
            },
            onFrameResult = { result ->
                Log.d(TAG, "📊 Frame 별 AI감지 결과: $result")
                _frameResults.tryEmit(result)
                sendTelemetry(result)
            },
            onReady = {
                if (!isModelReady) {
                    // 모델 로드가 완료되었을 때 화면 캡처(ImageReader) 시작
                    Log.d(TAG, "모델 로드 완료 및 ScreenCaptureManager 캡처 시작 지시")
                    isModelReady = true
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        startScreenCaptureInternal()
                    }
                }
            }
        )
        
        faceTracker.startDetection(
            onResult = {},
            onFrameResult = { result ->
                Log.d("FaceDetection", "🎯 OpenCV 얼굴 박스 갱신: $result")
                _frameResults.tryEmit(result)
            },
            onReady = {}
        )
    }

    private fun startScreenCaptureInternal() {
        val existing = screenCaptureManager
        if (existing != null) {
            if (existing.isCapturing) {
                // VirtualDisplay가 살아있음 → 일시정지만 해제
                Log.d(TAG, "기존 ScreenCaptureManager 재개 (pause 해제)")
                existing.resumeCapture()
                return
            } else {
                // MediaProjection.onStop() 등으로 VirtualDisplay가 이미 해제됨 → 재생성
                Log.w(TAG, "기존 ScreenCaptureManager 비활성 → 재생성")
                existing.stopCapture()
                screenCaptureManager = null
            }
        }

        val ctx = captureContext
        val projection = mediaProjection
        if (ctx == null || projection == null) {
            Log.w(TAG, "MediaProjection 없음 — 화면 캡처 불가 (감지는 Stub으로 동작)")
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                ctx?.let {
                    android.widget.Toast.makeText(it, "화면 캡처 권한이 만료되었습니다. 보호를 껐다 켜주세요.", android.widget.Toast.LENGTH_LONG).show()
                }
            }
            return
        }

        screenCaptureManager = ScreenCaptureManager(
            context = ctx,
            mediaProjection = projection,
            detectors = listOf(detector, faceTracker)
        )
        try {
            screenCaptureManager?.startCapture()
        } catch (e: SecurityException) {
            Log.e(TAG, "MediaProjection 토큰 만료/재사용 불가 — 캡처 시작 실패", e)
            screenCaptureManager = null
            mediaProjection = null
        }
    }

    override fun stopDetection() {
        Log.d(TAG, "감지 중단")
        isModelReady = false

        // ── 1) Detector 먼저 중단: 캡처 스레드의 processFrame이 결과를 emit하지 않도록 차단 ──
        detector.stopDetection()
        faceTracker.stopDetection()

        // ── 2) 화면 캡처 일시정지 (VirtualDisplay·MediaProjection 유지) ──
        screenCaptureManager?.pauseCapture()
    }

    override fun setMediaProjection(context: Context, mediaProjection: MediaProjection) {
        // 이미 동작 중인 구형 MediaProjection/ScreenCaptureManager가 있다면 완전히 파기 (화면 멈춤 버그 방지)
        if (this.mediaProjection != null || this.screenCaptureManager != null) {
            Log.d(TAG, "새로운 MediaProjection 수신: 기존 캡처 매니저 및 프로젝션 강제 종료")
            releaseMediaProjection()
        }
        
        this.captureContext = context
        this.mediaProjection = mediaProjection
        Log.d(TAG, "MediaProjection 설정 완료")

        // 모델이 이미 준비돼 있으면 즉시 캡처 시작
        // (setInAppCallActive→startDetection→onReady 이후에 MediaProjection이 늦게 도착하는 수신통화 케이스 대응)
        if (isModelReady) {
            Log.d(TAG, "모델 이미 준비됨 — MediaProjection 설정 후 즉시 캡처 시작")
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                startScreenCaptureInternal()
            }
        }
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
                
                val rawJson = result.rawResultJson ?: return@launch
                
                // 실제 NDK 추론이 완료되어 JSON 결과를 반환한 경우에만 텔레메트리 진행
                if (!rawJson.startsWith("{")) {
                    return@launch
                }

                val validFailureTypes = setOf("MODEL_LOAD_FAIL", "TIMEOUT", "INFERENCE_ERROR", "UNKNOWN")

                try {
                    val obj = JSONObject(rawJson)
                    if (obj.has("error")) {
                        val raw = obj.getString("error")
                        errorType = if (raw in validFailureTypes) raw else "UNKNOWN"
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
                // 직접 조회 가능한 하드웨어 상태
                if (temp == 0f) temp = DeviceStatusProvider.getDeviceTemperature(ctx)
                if (ram == 0) ram = DeviceStatusProvider.getAppPssMemory()
                if (thermal == 0) thermal = DeviceStatusProvider.getThermalThrottlingStatus(ctx)
                if (battery == 0f) battery = DeviceStatusProvider.getBatteryCurrentNow(ctx)

                val request = TelemetryRequest(
                    user_id = tokenManager.getUserId().toInt(),
                    model_type = "FACE",
                    model_version = "v6",
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
