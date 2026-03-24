package com.s14p21a404.shutter.domain.usecase

import com.s14p21a404.shutter.domain.repository.DeepfakeDetectionRepository
import com.s14p21a404.shutter.domain.repository.VideoCallRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach

/**
 * 영상통화 모니터링 UseCase
 *
 * 영상통화 상태를 관찰하여,
 * 영상통화 시작 시 딥페이크 감지를 시작하고, 종료 시 중단한다.
 * 화면 캡처는 startDetection/stopDetection 내부에서 자동 관리된다.
 */
class MonitorVideoCallUseCase(
    private val videoCallRepo: VideoCallRepository,
    private val detectionRepo: DeepfakeDetectionRepository
) {
    companion object {
        /** 영상통화 감지 후 실제 감지 시작까지 대기 시간 (ms) */
        private const val DETECTION_START_DELAY_MS = 2000L
    }

    /**
     * 영상통화 상태를 관찰하면서 캡처·감지를 자동 제어하는 Flow를 반환.
     * collect 해야 동작한다.
     */
    fun observeAndControl(): Flow<Boolean> = videoCallRepo.observeVideoCallState()
        .onEach { isVideoCall ->
            if (isVideoCall) {
                // 통화 시작 후 2초 대기 (화면 안정화)
                delay(DETECTION_START_DELAY_MS)
            }
        }

    fun startDetection() = detectionRepo.startDetection()
    fun stopDetection() = detectionRepo.stopDetection()
}
