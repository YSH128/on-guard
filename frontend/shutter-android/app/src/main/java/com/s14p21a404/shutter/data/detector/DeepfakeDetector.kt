package com.s14p21a404.shutter.data.detector

import com.s14p21a404.shutter.domain.model.DeepfakeDetectionResult

/**
 * 딥페이크 감지기 인터페이스
 *
 * Data 레이어에서 정의하는 기술적 추상화.
 * Repository 구현체가 이 인터페이스를 통해 실제 감지를 수행한다.
 *
 * 구현체:
 * - BroadcastTriggerDetector: ADB 명령으로 수동 트리거 (데모용)
 * - RealDeepfakeDetector: 실제 캡처 + JNI + C++ 추론
 */
interface DeepfakeDetector {

    /**
     * 감지 시작.
     * 결과가 나올 때마다 [onResult] 콜백이 호출된다.
     */
    fun startDetection(onResult: (DeepfakeDetectionResult) -> Unit)

    /**
     * 감지 중단. 리소스 정리.
     */
    fun stopDetection()

    /**
     * 캡처된 프레임을 감지기로 공급.
     * ScreenCaptureManager → Detector 직접 전달 경로.
     *
     * @param buffer    RGBA 프레임 byte[]
     * @param width     프레임 너비 (px)
     * @param height    프레임 높이 (px)
     * @param rowStride Image.Plane의 rowStride
     */
    fun feedFrame(buffer: ByteArray, width: Int, height: Int, rowStride: Int) { /* no-op by default */ }
}

