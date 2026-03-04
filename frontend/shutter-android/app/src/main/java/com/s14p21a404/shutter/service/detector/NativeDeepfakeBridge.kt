package com.s14p21a404.shutter.service.detector

/**
 * JNI 브릿지 — C++ request_deepfake_detection 호출
 *
 * NDK 담당자가 deepfake_jni.cpp를 구현하고 shutter-native 라이브러리를 빌드해야 동작함.
 * NDK 셋업 전까지는 이 클래스를 직접 사용하지 말 것 (UnsatisfiedLinkError 발생).
 * → 대신 RealDeepfakeDetector 내부에서만 호출되며, Stub 감지기 사용 시에는 로드되지 않음.
 */
class NativeDeepfakeBridge {

    /**
     * 프레임 + 오디오를 C++ NDK에 전달하여 딥페이크 감지 요청
     *
     * @param videoBuffer  RGBA 프레임 byte[] (ScreenCaptureManager에서 받은 그대로)
     * @param width        프레임 너비 (px)
     * @param height       프레임 높이 (px)
     * @param rowStride    Image.Plane의 rowStride (padding 포함 행 바이트 수)
     * @param audioBuffer  오디오 PCM byte[] (현재 null — 향후 구현)
     * @return JSON 문자열: {"detected":1,"roi":[0.3,0.22,0.45,0.567]}
     */
    external fun requestDeepfakeDetection(
        videoBuffer: ByteArray,
        width: Int,
        height: Int,
        rowStride: Int,
        audioBuffer: ByteArray?
    ): String

    companion object {
        private var loaded = false

        /**
         * 네이티브 라이브러리 로드.
         * NDK 빌드 전에는 호출하지 않도록 RealDeepfakeDetector에서만 사용.
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

