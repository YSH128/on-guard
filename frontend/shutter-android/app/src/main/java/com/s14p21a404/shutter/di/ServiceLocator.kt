package com.s14p21a404.shutter.di

import android.content.Context
import com.s14p21a404.shutter.data.detector.DeepfakeDetector
import com.s14p21a404.shutter.data.detector.real.RealDeepfakeDetector
import com.s14p21a404.shutter.data.detector.stub.BroadcastTriggerDetector
import com.s14p21a404.shutter.data.repository.DeepfakeDetectionRepositoryImpl
import com.s14p21a404.shutter.data.repository.VideoCallRepositoryImpl
import com.s14p21a404.shutter.domain.repository.DeepfakeDetectionRepository
import com.s14p21a404.shutter.domain.repository.VideoCallRepository
import com.s14p21a404.shutter.domain.usecase.DetectDeepfakeUseCase
import com.s14p21a404.shutter.domain.usecase.MonitorVideoCallUseCase
import com.s14p21a404.shutter.presentation.overlay.WarningOverlayManager

/**
 * 수동 DI 컨테이너
 *
 * 프로젝트 규모가 커지면 Hilt로 전환 권장.
 */
class ServiceLocator private constructor(context: Context) {

    // ── Data Layer ──

    // val detector: DeepfakeDetector = BroadcastTriggerDetector(context)       // ADB 테스트용
    val detector: DeepfakeDetector = RealDeepfakeDetector(context)       // JNI 파이프라인 (NDK SO 필요)

    val detectionRepository: DeepfakeDetectionRepository =
        DeepfakeDetectionRepositoryImpl(detector)

    val videoCallRepository: VideoCallRepository =
        VideoCallRepositoryImpl(context)

    // ── Domain Layer ──

    val monitorVideoCallUseCase = MonitorVideoCallUseCase(
        videoCallRepo = videoCallRepository,
        detectionRepo = detectionRepository
    )

    val detectDeepfakeUseCase = DetectDeepfakeUseCase(
        repository = detectionRepository
    )

    // ── Presentation Layer ──

    val warningOverlayManager = WarningOverlayManager(context)


    companion object {
        @Volatile
        private var instance: ServiceLocator? = null

        fun getInstance(context: Context): ServiceLocator =
            instance ?: synchronized(this) {
                instance ?: ServiceLocator(context.applicationContext).also { instance = it }
            }
    }
}

