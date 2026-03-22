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
     * 사전에 AI 모델을 초기화 및 컴파일/적재하여 첫 프레임 처리 속도를 향상시킵니다.
     */
    fun prepare() { /* no-op by default */ }

    /**
     * 감지 시작.
     * 결과가 나올 때마다 [onResult] 콜백이 호출된다.
     * 모델 초기화가 완료되어 프레임을 받을 준비가 되면 [onReady]가 호출된다.
     */
    fun startDetection(
        onResult: (DeepfakeDetectionResult) -> Unit,
        onFrameResult: (DeepfakeDetectionResult) -> Unit = {},
        onReady: () -> Unit = {}
    )

    /**
     * 감지 중단. 리소스 정리.
     */
    fun stopDetection()

    /**
     * 캡처된 프레임을 감지기에서 처리.
     * ScreenCaptureManager가 등록된 Detector들에 직접 호출한다.
     *
     * @param buffer    RGBA 프레임 byte[]
     * @param width     프레임 너비 (px)
     * @param height    프레임 높이 (px)
     * @param rowStride Image.Plane의 rowStride
     */
    fun processFrame(buffer: ByteArray, width: Int, height: Int, rowStride: Int) { /* no-op by default */ }
}

