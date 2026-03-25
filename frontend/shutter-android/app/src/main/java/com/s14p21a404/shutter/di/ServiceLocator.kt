package com.s14p21a404.shutter.di


import android.content.Context
import com.s14p21a404.shutter.data.detector.DeepfakeDetector
import com.s14p21a404.shutter.data.detector.real.RealDeepfakeDetector
import com.s14p21a404.shutter.data.repository.AlertRepositoryImpl
import com.s14p21a404.shutter.data.repository.AuthRepositoryImpl
import com.s14p21a404.shutter.data.repository.DeepfakeDetectionRepositoryImpl
import com.s14p21a404.shutter.data.repository.GpsRepositoryImpl
import com.s14p21a404.shutter.data.repository.GuardianRepositoryImpl
import com.s14p21a404.shutter.data.repository.CriminalLocationApi
import com.s14p21a404.shutter.data.repository.GuardianUuidStore
import com.s14p21a404.shutter.data.repository.VideoCallRepositoryImpl
import com.s14p21a404.shutter.data.token.TokenManager
import com.s14p21a404.shutter.domain.repository.AlertRepository
import com.s14p21a404.shutter.domain.repository.AuthRepository
import com.s14p21a404.shutter.domain.repository.DeepfakeDetectionRepository
import com.s14p21a404.shutter.domain.repository.GpsRepository
import com.s14p21a404.shutter.domain.repository.GuardianRepository
import com.s14p21a404.shutter.domain.repository.VideoCallRepository
import com.s14p21a404.shutter.network.GpsWebSocketClient
import com.s14p21a404.shutter.domain.usecase.AddGuardianUseCase
import com.s14p21a404.shutter.domain.usecase.DeleteGuardianUseCase
import com.s14p21a404.shutter.domain.usecase.DetectDeepfakeUseCase
import com.s14p21a404.shutter.domain.usecase.GetAccessTokenUseCase
import com.s14p21a404.shutter.domain.usecase.GetMyGuardiansUseCase
import com.s14p21a404.shutter.domain.usecase.GetMyWardsUseCase
import com.s14p21a404.shutter.domain.usecase.GetUserIdUseCase
import com.s14p21a404.shutter.domain.usecase.LoginUseCase
import com.s14p21a404.shutter.domain.usecase.LogoutUseCase
import com.s14p21a404.shutter.domain.usecase.SaveUserIdUseCase
import com.s14p21a404.shutter.domain.usecase.MonitorVideoCallUseCase
import com.s14p21a404.shutter.network.RetrofitClient
import com.s14p21a404.shutter.presentation.overlay.FinancialBlockOverlayManager
import com.s14p21a404.shutter.presentation.overlay.WarningOverlayManager
import com.s14p21a404.shutter.presentation.service.AlertSoundManager
import com.s14p21a404.shutter.presentation.service.DeepfakeAlertSender
import com.s14p21a404.shutter.presentation.screens.OnDeviceWhisperHelper

/**
 * 수동 DI 컨테이너
 *
 * 프로젝트 규모가 커지면 Hilt로 전환 권장.
 */
class ServiceLocator private constructor(context: Context) {

    // ── Data Layer ──

    val tokenManager = TokenManager(context)

    private val apiService = RetrofitClient.create(tokenManager)

    val authRepository: AuthRepository = AuthRepositoryImpl(
        apiService = apiService,
        tokenManager = tokenManager,
    )

    val guardianRepository: GuardianRepository = GuardianRepositoryImpl(
        apiService = apiService,
    )

    val gpsRepository: GpsRepository = GpsRepositoryImpl(apiService)

    val alertRepository: AlertRepository = AlertRepositoryImpl(apiService)

    val criminalLocationApi = CriminalLocationApi(apiService)

    val gpsWebSocketClient = GpsWebSocketClient(tokenManager)

    // val detector: DeepfakeDetector = BroadcastTriggerDetector(context)       // ADB 테스트용
    val detector: DeepfakeDetector = RealDeepfakeDetector(context)       // JNI 파이프라인 (NDK SO 필요)

    val detectionRepository: DeepfakeDetectionRepository =
        DeepfakeDetectionRepositoryImpl(
            detector = detector,
            telemetryApi = RetrofitClient.createTelemetryApi(tokenManager),
            tokenManager = tokenManager,
        )

    // ── Guardian UseCases ──

    val getMyGuardiansUseCase = GetMyGuardiansUseCase(guardianRepository)
    val addGuardianUseCase = AddGuardianUseCase(guardianRepository)
    val deleteGuardianUseCase = DeleteGuardianUseCase(guardianRepository)
    val getMyWardsUseCase = GetMyWardsUseCase(guardianRepository)

    val videoCallRepository: VideoCallRepository =
        VideoCallRepositoryImpl(context, apiService)

    // ── Auth UseCases ──

    val loginUseCase = LoginUseCase(authRepository)
    val logoutUseCase = LogoutUseCase(authRepository)
    val getAccessTokenUseCase = GetAccessTokenUseCase(authRepository)
    val saveUserIdUseCase = SaveUserIdUseCase(authRepository)
    val getUserIdUseCase = GetUserIdUseCase(authRepository)

    val monitorVideoCallUseCase = MonitorVideoCallUseCase(
        videoCallRepo = videoCallRepository,
        detectionRepo = detectionRepository
    )

    val detectDeepfakeUseCase = DetectDeepfakeUseCase(
        repository = detectionRepository
    )

    // ── Presentation Layer ──

    val warningOverlayManager = WarningOverlayManager(context)
    val financialBlockOverlayManager = FinancialBlockOverlayManager(context)
    val alertSoundManager = AlertSoundManager(context)
    val guardianUuidStore = GuardianUuidStore(context)
    val deepfakeAlertSender = DeepfakeAlertSender(guardianRepository, guardianUuidStore, alertRepository)

    // ── On-Device STT (WhisperKit) ──
    // lazy 초기화: 딥페이크 브릿지(libpytorch_jni.so)와 동시에 네이티브 라이브러리가 로딩되면
    // libc++_shared.so 충돌(std::bad_cast)이 발생하므로, 최초 접근 시점까지 로딩을 지연
    val whisperHelper by lazy { OnDeviceWhisperHelper(context) }


    companion object {
        @Volatile
        private var instance: ServiceLocator? = null

        fun getInstance(context: Context): ServiceLocator =
            instance ?: synchronized(this) {
                instance ?: ServiceLocator(context.applicationContext).also { instance = it }
            }
    }
}