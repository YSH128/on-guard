package com.s14p21a404.shutter.data.detector.bridge

import android.widget.TextView

/**
 * JNI 브릿지 (Kotlin 쪽) — NDK 네이티브 함수 request_deepfake_detection 호출
 *
 * ┌────────────────────────────────────────────────────────┐
 * │  담당 경계                                              │
 * │  • 이 파일 (NativeDeepfakeBridge.kt)  → 앱(Kotlin) 담당 │
 * │  • shutter-native SO + deepfake_jni.cpp → NDK 담당      │
 * └────────────────────────────────────────────────────────┘
 *
 * NDK 담당자와 합의한 JNI 함수 시그니처:
 *   request_deepfake_detection(type: String, buffer: ByteArray, resultTextView: TextView)
 *
 * - type: 감지 유형 ("FACE" / "DEEPVOICE")
 * - buffer: RGBA 프레임 byte[] (ScreenCaptureManager에서 캡처한 그대로)
 * - resultTextView: 결과를 표시할 TextView (현재 더미 — 향후 NDK 쪽에서 활용 예정)
 */
class NativeDeepfakeBridge {

    /**
     * NDK AI 추론 요청
     *
     * @param type           감지 유형 ("FACE" = 영상 딥페이크, "DEEPVOICE" = 음성 딥페이크)
     * @param buffer         RGBA 프레임 byte[] (캡처된 프레임 버퍼 그대로)
     * @param resultTextView 결과 표시용 TextView (현재 더미)
     */
    external fun request_deepfake_detection(
        type: String,
        buffer: ByteArray,
        resultTextView: TextView
    )

    companion object {
        private var loaded = false

        /**
         * 네이티브 라이브러리 로드.
         *
         * @return true: 로드 성공, false: 로드 실패 (NDK 미빌드)
         */
        fun loadLibrary(): Boolean {
            if (loaded) return true
            return try {
                System.loadLibrary("shutter-native")
                loaded = true
                true
            } catch (e: UnsatisfiedLinkError) {
                false
            }
        }
    }
}

