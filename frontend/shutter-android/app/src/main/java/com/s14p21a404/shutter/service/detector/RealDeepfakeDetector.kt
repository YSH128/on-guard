package com.s14p21a404.shutter.service.detector

import android.util.Log
import org.json.JSONObject

/**
 * 실제 딥페이크 감지기 — JNI를 통해 C++ NDK AI 모듈 호출
 *
 * 동작 흐름:
 *   ScreenCaptureManager.onFrameCaptured
 *       → feedFrame(buffer, width, height, rowStride)
 *           → NativeDeepfakeBridge.requestDeepfakeDetection()  [JNI]
 *               → C++ request_deepfake_detection(videoData, audioData)
 *           → JSON 파싱
 *           → onResult 콜백
 *
 * NDK 라이브러리(shutter-native)가 빌드되지 않은 환경에서는
 * 로드 실패를 감지하여 안전하게 스킵한다.
 */
class RealDeepfakeDetector : DeepfakeDetector {

    private var bridge: NativeDeepfakeBridge? = null
    private var onResultCallback: ((DeepfakeDetectionResult) -> Unit)? = null
    private var nativeAvailable = false

    override fun startDetection(onResult: (DeepfakeDetectionResult) -> Unit) {
        onResultCallback = onResult

        // 네이티브 라이브러리 로드 시도
        nativeAvailable = NativeDeepfakeBridge.loadLibrary()
        if (nativeAvailable) {
            bridge = NativeDeepfakeBridge()
            Log.d(TAG, "RealDeepfakeDetector 시작 — NDK 라이브러리 로드 성공")
        } else {
            Log.w(TAG, "RealDeepfakeDetector 시작 — NDK 라이브러리 없음 (JNI 호출 스킵)")
        }
    }

    /**
     * ScreenCaptureManager의 onFrameCaptured 콜백에서 호출.
     * 캡처된 프레임을 JNI로 전달하고, JSON 결과를 파싱하여 onResult 콜백을 호출한다.
     *
     * @param buffer    RGBA 프레임 byte[] (padding 포함)
     * @param width     프레임 너비 (px)
     * @param height    프레임 높이 (px)
     * @param rowStride Image.Plane의 rowStride
     */
    fun feedFrame(buffer: ByteArray, width: Int, height: Int, rowStride: Int) {
        val callback = onResultCallback ?: return

        if (!nativeAvailable || bridge == null) {
            Log.v(TAG, "NDK 미로드 — 프레임 스킵 (${width}x${height})")
            return
        }

        try {
            // JNI 호출 (오디오는 현재 null)
            val jsonResult: String = bridge!!.requestDeepfakeDetection(
                videoBuffer = buffer,
                width = width,
                height = height,
                rowStride = rowStride,
                audioBuffer = null  // 향후 오디오 캡처 구현 시 전달
            )

            Log.d(TAG, "JNI 응답: $jsonResult")

            // JSON 파싱 → DeepfakeDetectionResult
            val result = parseResult(jsonResult)
            callback(result)

        } catch (e: Exception) {
            Log.e(TAG, "JNI 호출 또는 파싱 실패", e)
        }
    }

    override fun stopDetection() {
        onResultCallback = null
        bridge = null
        Log.d(TAG, "RealDeepfakeDetector 중단")
    }

    /**
     * AI JSON 응답 파싱
     *
     * 예상 형식:
     * ```json
     * { "detected": 1, "roi": [0.3, 0.22, 0.45, 0.567] }
     * ```
     */
    private fun parseResult(json: String): DeepfakeDetectionResult {
        val obj = JSONObject(json)

        val detected = obj.optInt("detected", 0) == 1

        val roiArray = obj.optJSONArray("roi")
        val roi = if (roiArray != null && roiArray.length() == 4) {
            FloatArray(4) { roiArray.getDouble(it).toFloat() }
        } else null

        return DeepfakeDetectionResult(detected = detected, roi = roi)
    }

    companion object {
        private const val TAG = "RealDeepfakeDetector"
    }
}

