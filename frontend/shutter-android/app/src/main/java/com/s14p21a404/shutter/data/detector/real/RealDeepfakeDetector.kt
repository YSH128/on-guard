package com.s14p21a404.shutter.data.detector.real

import android.content.Context
import android.util.Log
import android.widget.TextView
import com.s14p21a404.shutter.data.detector.DeepfakeDetector
import com.s14p21a404.shutter.data.detector.bridge.NativeDeepfakeBridge
import com.s14p21a404.shutter.domain.model.DeepfakeDetectionResult

/**
 * 실제 딥페이크 감지기 — NativeDeepfakeBridge(JNI)를 호출하여 AI 추론 요청
 *
 * ┌──────────────────────────────────────────────────────────┐
 * │  이 클래스의 역할 (앱/Kotlin 담당)                         │
 * │  1. RepositoryImpl에서 디스패치된 프레임(byte[])을 받아     │
 * │  2. NativeDeepfakeBridge.request_deepfake_detection() 호출│
 * │  3. 반환된 JSON 문자열 읽어 파싱                            │
 * │  4. 결과를 onResult 콜백으로 전달                          │
 * ├──────────────────────────────────────────────────────────┤
 * │  NDK 담당 (이 클래스 바깥)                                 │
 * │  • libshutter-native.so 빌드 (deepfake_jni.cpp)          │
 * │  • request_deepfake_detection 에서 네이티브 C++ 추론 수행   │
 * └──────────────────────────────────────────────────────────┘
 */
class RealDeepfakeDetector(private val context: Context) : DeepfakeDetector {

    private var bridge: NativeDeepfakeBridge? = null
    @Volatile
    private var onResultCallback: ((DeepfakeDetectionResult) -> Unit)? = null
    @Volatile
    private var onFrameResultCallback: ((DeepfakeDetectionResult) -> Unit)? = null
    private var nativeAvailable = false

    /** stopDetection 호출 후 true — processFrame에서 결과 emit을 차단한다. */
    @Volatile
    private var stopped = true

    /** NDK 결과를 받을 더미 TextView (NDK 쪽에서 setText로 결과를 써줄 예정) -> 폐기: 직관적인 String Return 사용 */

    // 추론 결과 누적용
    private val resultBuffer = mutableListOf<DeepfakeDetectionResult>()
    private var detectionStartTime = 0L

    override fun prepare() {
        if (bridge == null) {
            nativeAvailable = NativeDeepfakeBridge.loadLibrary()
            if (nativeAvailable) {
                bridge = NativeDeepfakeBridge(context)
                bridge!!.compileQnnCache()
                Log.d(TAG, "RealDeepfakeDetector 사전 컴파일 시작 (백그라운드 캐시화)")
            }
        }
    }

    override fun startDetection(
        onResult: (DeepfakeDetectionResult) -> Unit,
        onFrameResult: (DeepfakeDetectionResult) -> Unit,
        onReady: () -> Unit
    ) {
        stopped = false
        onResultCallback = onResult
        onFrameResultCallback = onFrameResult
        resultBuffer.clear()  // 시작 시 초기화
        detectionStartTime = System.currentTimeMillis()
        isFakeDetected = false
        lastFakeProb = 0f
        
        if (bridge == null) {
            nativeAvailable = NativeDeepfakeBridge.loadLibrary()
            if (nativeAvailable) {
                bridge = NativeDeepfakeBridge(context)
            } else {
                Log.w(TAG, "RealDeepfakeDetector 시작 — NDK 라이브러리 없음 (JNI 호출 스킵)")
            }
        }
        
        if (nativeAvailable && bridge != null) {
            bridge!!.loadModelIntoMemory {
                onReady()
            }
            Log.d(TAG, "RealDeepfakeDetector 시작 — NDK 모델 로드 지시 및 대기")
        } else {
            onReady() // JNI 사용 불가 시에도 프레임 처리를 뚫어주기 위해 즉시 리턴
        }
    }

    /**
     * RepositoryImpl에서 디스패치된 프레임을 받아 JNI로 전달한다.
     *
     * 1. request_deepfake_detection("FACE", buffer, dummyTextView) 호출
     * 2. dummyTextView.text를 읽어 결과 파싱
     *
     * @param buffer    RGBA 프레임 byte[] (padding 포함)
     * @param width     프레임 너비 (px)
     * @param height    프레임 높이 (px)
     * @param rowStride Image.Plane의 rowStride
     */
    @Volatile
    private var isInferencing = false
    @Volatile
    private var lastFakeProb = 0f
    @Volatile
    private var isFakeDetected = false
    @Volatile
    private var lastAiInferenceStartTime = 0L

    override fun processFrame(buffer: ByteArray, width: Int, height: Int, rowStride: Int) {
        // 감지 중단 후 캡처 스레드에서 늦게 도착하는 프레임을 즉시 버린다.
        if (stopped) return
        val callback = onResultCallback ?: return

        if (!nativeAvailable || bridge == null) {
            Log.d(TAG, "✅ processFrame 도달 (${width}x${height}, ${buffer.size}bytes) — NDK SO 미로드로 JNI 호출 대기 중")
            return
        }

        if (!bridge!!.isModelReady) {
            Log.d(TAG, "⏳ 메모리에 AI 모델 적재 중입니다... 프레임 추론 건너뜀")
            // 화면 쪽으로 로딩 상태를 전달하기 위해 가짜 결과 발행 (단, 최종 callback이 아닌 프레임별 callback으로 넘겨야 전체 사이클이 중단되지 않음)
            onFrameResultCallback?.invoke(DeepfakeDetectionResult(detected = false, rawResultJson = "AI 모델 빌드 및 적재 중입니다..."))
            return
        }

        // ── 1. 무거운 AI 추론(6초 간격 제한 적용) — 주기적으로 JNI 스레드 위임 ──
        val now = System.currentTimeMillis()
        if (isInferencing || (now - lastAiInferenceStartTime < 6000L)) {
            // 이 프레임은 JNI 추론을 건너뜀 (6초가 지나지 않았으므로 무시)
            return
        }

        // ── 3. 무거운 AI 추론(6초 간격 제한 적용)은 별도 백그라운드 스레드로 위임 (블로킹 방지) ──
        isInferencing = true
        lastAiInferenceStartTime = now
        Thread {
            try {
                if (stopped) return@Thread

                val resultText = bridge!!.request_deepfake_detection("FACE", buffer, width, height, rowStride)
                Log.d(TAG, "JNI 결과: $resultText")

                var result = parseResult(resultText)

                if (stopped) return@Thread

                lastFakeProb = result.fakeProb

                onFrameResultCallback?.invoke(result)

                // 버퍼 집계는 Thread-Safe하게 변경
                synchronized(resultBuffer) {
                    resultBuffer.add(result)
                    Log.d(TAG, "결과 누적 ${resultBuffer.size}/$BUFFER_SIZE")

                    if (resultBuffer.size >= BUFFER_SIZE) {
                        val averaged = averageResults(resultBuffer.toList())
                        
                        isFakeDetected = averaged.detected
                        
                        // finalResult에는 CAM ROI를 절대 넣지 않음 (OpenCV 전용)
                        val finalResult = averaged.copy(
                            roi = null,
                            fakeProb = averaged.fakeProb
                        )
                        resultBuffer.clear()
                        callback(finalResult)
                        val elapsedFromStart = System.currentTimeMillis() - detectionStartTime
                        Log.d(TAG, "평균 결과(통화 시작 후 ${elapsedFromStart}ms): $finalResult")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "JNI 호출 실패", e)
            } finally {
                isInferencing = false
            }
        }.start()
    }

    private fun averageResults(results: List<DeepfakeDetectionResult>): DeepfakeDetectionResult {
        // detected: 과반수(5개 이상)가 true면 true
        val avgFakeProb = results.map { it.fakeProb }.average().toFloat()
        val detected = avgFakeProb >= 0.5f // 진짜일 확률이 0.5보다 크면 진짜, 작으면 가짜

        // ROI는 averageResults에서 계산하지 않음 — 얼굴 박스는 OpenCV 전용
        return DeepfakeDetectionResult(
            detected = detected,
            fakeProb = avgFakeProb,
            roi = null,
            rawResultJson = "averaged $BUFFER_SIZE frames (fakeProb: $avgFakeProb)"
        )
    }

    override fun stopDetection() {
        stopped = true      // 캡처 스레드의 processFrame이 즉시 bail-out 하도록 먼저 설정
        onResultCallback = null
        onFrameResultCallback = null
        resultBuffer.clear()
        Log.d(TAG, "RealDeepfakeDetector 중단")
    }

    /**
     * NDK 결과 파싱
     *
     * NDK가 TextView에 써주는 결과 형식 (예상):
     *   - JSON: {"detected":1,"roi":[0.3,0.22,0.45,0.567]}
     *   - 또는 단순 텍스트: "1" / "0"
     *   - 또는 빈 문자열 (아직 미구현)
     *
     * 형식이 확정되지 않았으므로 유연하게 파싱한다.
     */
    private fun parseResult(text: String): DeepfakeDetectionResult {
        if (text.isBlank()) {
            Log.w(TAG, "NDK 결과 비어있음 — 미검출 처리")
            return DeepfakeDetectionResult(detected = false, rawResultJson = text)
        }

        // JSON 형식 시도
        if (text.trimStart().startsWith("{")) {
            try {
                val obj = org.json.JSONObject(text)
                val detected = obj.optInt("detected", 0) == 1
                val fakeProb = obj.optDouble("fakeProb", 0.0).toFloat()
                Log.w(TAG, "fakeProb : ${fakeProb}")

                // CAM ROI는 무시 — 얼굴 박스는 OpenCV detectMultiScale 전용
                return DeepfakeDetectionResult(detected = detected, fakeProb = fakeProb, roi = null, rawResultJson = text)
            } catch (e: Exception) {
                Log.w(TAG, "JSON 파싱 실패, 단순 텍스트로 시도: $text", e)
            }
        }

        // 단순 텍스트 ("1" = 감지, 그 외 = 미감지)
        val detected = text.trim() == "1"
        return DeepfakeDetectionResult(detected = detected, rawResultJson = text)
    }

    companion object {
        private const val TAG = "RealDeepfakeDetector"
        private const val BUFFER_SIZE = 7 // 원래 10
    }
}