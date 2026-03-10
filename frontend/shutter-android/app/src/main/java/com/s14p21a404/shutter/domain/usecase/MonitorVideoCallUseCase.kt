package com.s14p21a404.shutter.domain.usecase

import com.s14p21a404.shutter.domain.repository.DeepfakeDetectionRepository
import com.s14p21a404.shutter.domain.repository.VideoCallRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach

/**
 * 영상통화 모니터링 UseCase
 *
 * 영상통화 상태를 관찰하여,
 * 영상통화 시작 시 딥페이크 감지를 시작하고, 종료 시 감지를 중단한다.
 */
class MonitorVideoCallUseCase(
    private val videoCallRepo: VideoCallRepository,
    private val detectionRepo: DeepfakeDetectionRepository
) {
    /**
     * 영상통화 상태를 관찰하면서 감지를 자동 제어하는 Flow를 반환.
     * collect 해야 동작한다.
     */
    fun observeAndControl(): Flow<Boolean> = videoCallRepo.observeVideoCallState()
        .onEach { isVideoCall ->
            if (isVideoCall) detectionRepo.startDetection()
            else detectionRepo.stopDetection()
        }
}

