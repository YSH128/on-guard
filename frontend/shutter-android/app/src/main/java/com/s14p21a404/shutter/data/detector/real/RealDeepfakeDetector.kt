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
 * │  3. resultTextView에 NDK가 써주는 결과를 읽어 파싱          │
 * │  4. 결과를 onResult 콜백으로 전달                          │
 * ├──────────────────────────────────────────────────────────┤
 * │  NDK 담당 (이 클래스 바깥)                                 │
 * │  • libshutter-native.so 빌드 (deepfake_jni.cpp)          │
 * │  • C++ request_deepfake_detection(type, buffer, tv) 구현  │
 * └──────────────────────────────────────────────────────────┘
 */
class RealDeepfakeDetector(private val context: Context) : DeepfakeDetector {

    private var bridge: NativeDeepfakeBridge? = null
    private var onResultCallback: ((DeepfakeDetectionResult) -> Unit)? = null
    private var nativeAvailable = false

    /** NDK 결과를 받을 더미 TextView (NDK 쪽에서 setText로 결과를 써줄 예정) */
    private var dummyResultTextView: TextView? = null

    override fun startDetection(onResult: (DeepfakeDetectionResult) -> Unit) {
        onResultCallback = onResult

        nativeAvailable = NativeDeepfakeBridge.loadLibrary()
        if (nativeAvailable) {
            bridge = NativeDeepfakeBridge(context)
            dummyResultTextView = TextView(context)
            Log.d(TAG, "RealDeepfakeDetector 시작 — NDK 라이브러리 로드 성공")
        } else {
            Log.w(TAG, "RealDeepfakeDetector 시작 — NDK 라이브러리 없음 (JNI 호출 스킵)")
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
    override fun processFrame(buffer: ByteArray, width: Int, height: Int, rowStride: Int) {
        val callback = onResultCallback ?: return

        if (!nativeAvailable || bridge == null) {
            Log.d(TAG, "✅ processFrame 도달 (${width}x${height}, ${buffer.size}bytes) — NDK SO 미로드로 JNI 호출 대기 중")
            return
        }

        try {
            // ── 1) JNI 호출: buffer 직접 전달 ──
            val tv = dummyResultTextView!!
            tv.text = ""  // 초기화

            bridge!!.request_deepfake_detection("FACE", buffer, tv)

            // ── 2) 결과 읽기 ──
            val resultText = tv.text.toString()
            Log.d(TAG, "JNI 결과 (TextView): $resultText")

            val result = parseResult(resultText)
            callback(result)

        } catch (e: Exception) {
            Log.e(TAG, "JNI 호출 또는 파싱 실패", e)
        }
    }

    override fun stopDetection() {
        onResultCallback = null
        bridge = null
        dummyResultTextView = null
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
            return DeepfakeDetectionResult(detected = false)
        }

        // JSON 형식 시도
        if (text.trimStart().startsWith("{")) {
            try {
                val obj = org.json.JSONObject(text)
                val detected = obj.optInt("detected", 0) == 1
                val roiArray = obj.optJSONArray("roi")
                val roi = if (roiArray != null && roiArray.length() == 4) {
                    FloatArray(4) { roiArray.getDouble(it).toFloat() }
                } else null
                return DeepfakeDetectionResult(detected = detected, roi = roi)
            } catch (e: Exception) {
                Log.w(TAG, "JSON 파싱 실패, 단순 텍스트로 시도: $text", e)
            }
        }

        // 단순 텍스트 ("1" = 감지, 그 외 = 미감지)
        val detected = text.trim() == "1"
        return DeepfakeDetectionResult(detected = detected)
    }

    companion object {
        private const val TAG = "RealDeepfakeDetector"
    }
}

