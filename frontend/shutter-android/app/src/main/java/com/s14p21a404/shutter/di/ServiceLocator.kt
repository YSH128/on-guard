package com.s14p21a404.shutter.di

import android.content.Context
import com.s14p21a404.shutter.data.detector.DeepfakeDetector
import com.s14p21a404.shutter.data.detector.real.RealDeepfakeDetector
import com.s14p21a404.shutter.data.repository.AuthRepositoryImpl
import com.s14p21a404.shutter.data.repository.DeepfakeDetectionRepositoryImpl
import com.s14p21a404.shutter.data.repository.VideoCallRepositoryImpl
import com.s14p21a404.shutter.data.token.TokenManager
import com.s14p21a404.shutter.domain.repository.AuthRepository
import com.s14p21a404.shutter.domain.repository.DeepfakeDetectionRepository
import com.s14p21a404.shutter.domain.repository.VideoCallRepository
import com.s14p21a404.shutter.domain.usecase.DetectDeepfakeUseCase
import com.s14p21a404.shutter.domain.usecase.GetAccessTokenUseCase
import com.s14p21a404.shutter.domain.usecase.LoginUseCase
import com.s14p21a404.shutter.domain.usecase.LogoutUseCase
import com.s14p21a404.shutter.domain.usecase.MonitorVideoCallUseCase
import com.s14p21a404.shutter.network.RetrofitClient
import com.s14p21a404.shutter.presentation.overlay.WarningOverlayManager

/**
 * 수동 DI 컨테이너
 *
 * 프로젝트 규모가 커지면 Hilt로 전환 권장.
 */
class ServiceLocator private constructor(context: Context) {

    // ── Data Layer ──

    val tokenManager = TokenManager(context)

    val authRepository: AuthRepository = AuthRepositoryImpl(
        apiService = RetrofitClient.create(tokenManager),
        tokenManager = tokenManager,
    )

    // val detector: DeepfakeDetector = BroadcastTriggerDetector(context)       // ADB 테스트용
    val detector: DeepfakeDetector = RealDeepfakeDetector(context)       // JNI 파이프라인 (NDK SO 필요)

    val detectionRepository: DeepfakeDetectionRepository =
        DeepfakeDetectionRepositoryImpl(detector)

    val videoCallRepository: VideoCallRepository =
        VideoCallRepositoryImpl(context)

    // ── Domain Layer ──

    val loginUseCase = LoginUseCase(authRepository)
    val logoutUseCase = LogoutUseCase(authRepository)
    val getAccessTokenUseCase = GetAccessTokenUseCase(authRepository)

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