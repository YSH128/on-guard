package com.s14p21a404.shutter.presentation.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.s14p21a404.shutter.data.capture.ScreenCaptureManager
import com.s14p21a404.shutter.di.ServiceLocator
import com.s14p21a404.shutter.domain.repository.DeepfakeDetectionRepository
import com.s14p21a404.shutter.domain.repository.VideoCallRepository
import com.s14p21a404.shutter.domain.usecase.DetectDeepfakeUseCase
import com.s14p21a404.shutter.domain.usecase.MonitorVideoCallUseCase
import com.s14p21a404.shutter.presentation.overlay.WarningOverlayManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 영상통화 감지 Foreground Service (얇은 껍데기)
 *
 * 비즈니스 로직은 UseCase에 위임하고,
 * 이 Service는 생명주기 관리 + Foreground 알림만 담당한다.
 */
class VideoCallDetectService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var monitorUseCase: MonitorVideoCallUseCase
    private lateinit var detectUseCase: DetectDeepfakeUseCase
    private lateinit var warningOverlayManager: WarningOverlayManager
    private lateinit var videoCallRepository: VideoCallRepository
    private lateinit var detectionRepository: DeepfakeDetectionRepository

    // ── 화면 캡처 ──
    private var mediaProjection: MediaProjection? = null
    private var screenCaptureManager: ScreenCaptureManager? = null

    // ── 생명주기 ──

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "서비스 생성")

        val locator = ServiceLocator.getInstance(this)
        monitorUseCase = locator.monitorVideoCallUseCase
        detectUseCase = locator.detectDeepfakeUseCase
        warningOverlayManager = locator.warningOverlayManager
        videoCallRepository = locator.videoCallRepository
        detectionRepository = locator.detectionRepository

        startAsForeground()

        // 영상통화 모니터링 시작
        videoCallRepository.startMonitoring()

        // 영상통화 상태 관찰 → 감지 자동 시작/중단
        serviceScope.launch {
            monitorUseCase.observeAndControl().collect { isVideoCall ->
                Log.d(TAG, "영상통화 상태: $isVideoCall")
                if (isVideoCall) {
                    startScreenCapture()
                } else {
                    screenCaptureManager?.stopCapture()
                    screenCaptureManager = null
                    warningOverlayManager.dismissWarning()
                }
            }
        }

        // 감지 결과 관찰 → 오버레이 표시/숨김
        serviceScope.launch {
            detectUseCase.observeResults().collect { result ->
                Log.d(TAG, "📊 감지 결과: $result")
                warningOverlayManager.onDetectionResult(result)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // MainActivity에서 전달한 MediaProjection 데이터로 초기화
        if (intent != null && intent.hasExtra(EXTRA_RESULT_CODE)) {
            val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
            val projectionData = intent.getParcelableExtra(EXTRA_PROJECTION_DATA, Intent::class.java)
            if (projectionData != null) {
                startAsForeground(includeMediaProjection = true)

                val projectionManager = getSystemService(MediaProjectionManager::class.java)
                mediaProjection = projectionManager.getMediaProjection(resultCode, projectionData)
                Log.d(TAG, "MediaProjection 초기화 완료")
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "서비스 종료")

        serviceScope.cancel()
        detectionRepository.stopDetection()
        videoCallRepository.stopMonitoring()
        screenCaptureManager?.stopCapture()
        screenCaptureManager = null
        mediaProjection?.stop()
        mediaProjection = null
        warningOverlayManager.dismissWarning()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── 화면 캡처 ──

    private fun startScreenCapture() {
        val projection = mediaProjection
        if (projection == null) {
            Log.w(TAG, "MediaProjection 없음 — 화면 캡처 불가 (감지는 Stub으로 동작)")
            return
        }

        screenCaptureManager = ScreenCaptureManager(
            context = this,
            mediaProjection = projection,
            detectionRepository = detectionRepository
        )
        screenCaptureManager?.startCapture()
    }

    // ── Foreground Service 설정 ──

    private fun startAsForeground(includeMediaProjection: Boolean = false) {
        val channelId = "shutter_detect_channel"
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            channelId,
            "Shutter 보호 서비스",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "영상통화 감지 모니터링 중"
        }
        notificationManager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Shutter 보호 활성화")
            .setContentText("영상통화를 모니터링 중입니다.")
            .setOngoing(true)
            .build()

        val serviceType = if (includeMediaProjection) {
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        } else {
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        }

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            serviceType
        )
    }

    companion object {
        private const val TAG = "VideoCallDetectService"
        private const val NOTIFICATION_ID = 1001

        /** MainActivity → Service: MediaProjection 결과 코드 */
        const val EXTRA_RESULT_CODE = "extra_result_code"
        /** MainActivity → Service: MediaProjection Intent 데이터 */
        const val EXTRA_PROJECTION_DATA = "extra_projection_data"
    }
}

