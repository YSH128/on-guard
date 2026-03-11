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
    /** 감지 결과 Flow */
    fun observeResults(): Flow<DeepfakeDetectionResult> =
        repository.observeDetectionResults()
}

