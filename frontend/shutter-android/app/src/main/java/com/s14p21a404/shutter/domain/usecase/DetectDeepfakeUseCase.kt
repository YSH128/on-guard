package com.s14p21a404.shutter.domain.usecase

import com.s14p21a404.shutter.domain.model.DeepfakeDetectionResult
import com.s14p21a404.shutter.domain.repository.DeepfakeDetectionRepository
import kotlinx.coroutines.flow.Flow

/**
 * 딥페이크 감지 결과 관찰 UseCase
 */
class DetectDeepfakeUseCase(
    private val repository: DeepfakeDetectionRepository
) {
    /** 사이클(10프레임) 집계 결과 Flow */
    fun observeResults(): Flow<DeepfakeDetectionResult> =
        repository.observeDetectionResults()

    /** 프레임별 실시간 결과 Flow */
    fun observeFrameResults(): Flow<DeepfakeDetectionResult> =
        repository.observeFrameResults()
}

