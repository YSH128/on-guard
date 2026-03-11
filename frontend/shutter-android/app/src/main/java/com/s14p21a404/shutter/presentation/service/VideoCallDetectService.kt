package com.s14p21a404.shutter.presentation.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
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
 *
 * 화면 캡처는 Data 레이어(DeepfakeDetectionRepository)의
 * startDetection/stopDetection 내부에서 자동 관리되므로, 이 Service에서는 MediaProjection만 전달한다.
 */
class VideoCallDetectService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var monitorUseCase: MonitorVideoCallUseCase
    private lateinit var detectUseCase: DetectDeepfakeUseCase
    private lateinit var warningOverlayManager: WarningOverlayManager
    private lateinit var videoCallRepository: VideoCallRepository
    private lateinit var detectionRepository: DeepfakeDetectionRepository

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

        // 영상통화 상태 관찰 → 캡처·감지 자동 시작/중단 (data layer 내부에서 처리)
        serviceScope.launch {
            monitorUseCase.observeAndControl().collect { isVideoCall ->
                Log.d(TAG, "영상통화 상태: $isVideoCall")
                if (!isVideoCall) {
                    warningOverlayManager.dismissWarning()
                }
            }
        }

        // 감지 결과 관찰 → 오버레이 표시/숨김
        serviceScope.launch {
            detectUseCase.observeResults().collect { result ->
                Log.d(TAG, "📊 감지 결과: $result")
                val roiBitmap = if (result.detected && result.hasRoi) {
                    cropRoiBitmap(result)
                } else null
                warningOverlayManager.onDetectionResult(result, roiBitmap)
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
                val mediaProjection = projectionManager.getMediaProjection(resultCode, projectionData)
                Log.d(TAG, "MediaProjection 초기화 완료")

                // Data 레이어에 MediaProjection 전달 (캡처는 data layer 내부에서 관리)
                if (mediaProjection != null) {
                    detectionRepository.setMediaProjection(this, mediaProjection)
                } else {
                    Log.w(TAG, "MediaProjection 획득 실패")
                }
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
        detectionRepository.releaseMediaProjection()
        warningOverlayManager.dismissWarning()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── ROI 크롭 ──

    /**
     * 최신 캡처 프레임에서 ROI 영역을 잘라낸 Bitmap을 반환한다.
     * ROI 좌표는 0.0~1.0 상대좌표이므로 실제 픽셀 좌표로 변환한다.
     */
    private fun cropRoiBitmap(result: com.s14p21a404.shutter.domain.model.DeepfakeDetectionResult): Bitmap? {
        val source = detectionRepository.getLatestBitmap() ?: return null
        return try {
            val w = source.width
            val h = source.height
            val left   = (result.roiMinX * w).toInt().coerceIn(0, w)
            val top    = (result.roiMinY * h).toInt().coerceIn(0, h)
            val right  = (result.roiMaxX * w).toInt().coerceIn(0, w)
            val bottom = (result.roiMaxY * h).toInt().coerceIn(0, h)
            val cropW  = (right - left).coerceAtLeast(1)
            val cropH  = (bottom - top).coerceAtLeast(1)
            Bitmap.createBitmap(source, left, top, cropW, cropH)
        } catch (e: Exception) {
            Log.e(TAG, "ROI 크롭 실패", e)
            null
        }
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

