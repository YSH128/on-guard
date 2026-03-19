package com.s14p21a404.shutter.presentation.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.s14p21a404.shutter.data.token.TokenManager
import com.s14p21a404.shutter.di.ServiceLocator
import com.s14p21a404.shutter.domain.repository.DeepfakeDetectionRepository
import com.s14p21a404.shutter.domain.repository.GpsRepository
import com.s14p21a404.shutter.domain.repository.VideoCallRepository
import com.s14p21a404.shutter.domain.usecase.DetectDeepfakeUseCase
import com.s14p21a404.shutter.domain.usecase.MonitorVideoCallUseCase
import com.s14p21a404.shutter.presentation.overlay.FinancialBlockOverlayManager
import com.s14p21a404.shutter.presentation.overlay.WarningOverlayManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

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
    private lateinit var financialBlockOverlayManager: FinancialBlockOverlayManager
    private lateinit var alertSoundManager: AlertSoundManager
    private lateinit var videoCallRepository: VideoCallRepository
    private lateinit var detectionRepository: DeepfakeDetectionRepository
    private lateinit var gpsRepository: GpsRepository
    private lateinit var tokenManager: TokenManager

    // GPS 전송 세션 관리
    private var gpsSendingJob: Job? = null
    private var gpsSessionStartedAt: Long? = null

    /** 포그라운드 앱 모니터링 Job — 영상통화 감지 시 시작, 종료 시 취소 */
    private var foregroundMonitorJob: Job? = null

    /** 금융 앱 포그라운드 모니터링 Job — 딥페이크 감지 시 시작, 종료 시 취소 */
    private var financialAppMonitorJob: Job? = null

    /**
     * 딥페이크가 감지되었는지 여부.
     * 금융 앱 차단 오버레이는 딥페이크 감지 + 금융 앱 포그라운드 시에만 표시한다.
     */
    @Volatile
    private var isDeepfakeDetected = false

    /**
     * 오버레이가 표시 중이었는지 여부.
     * 영상통화 화면을 벗어났다가 돌아올 때 오버레이를 복원하기 위해 사용.
     */
    @Volatile
    private var wasOverlayShowing = false

    // ── 생명주기 ──

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "서비스 생성")

        val locator = ServiceLocator.getInstance(this)
        monitorUseCase = locator.monitorVideoCallUseCase
        detectUseCase = locator.detectDeepfakeUseCase
        warningOverlayManager = locator.warningOverlayManager
        financialBlockOverlayManager = locator.financialBlockOverlayManager
        alertSoundManager = locator.alertSoundManager
        videoCallRepository = locator.videoCallRepository
        detectionRepository = locator.detectionRepository
        gpsRepository = locator.gpsRepository
        tokenManager = locator.tokenManager

        startAsForeground()

        // 영상통화 모니터링 시작
        videoCallRepository.startMonitoring()

        // 영상통화 상태 관찰 → 캡처·감지 자동 시작/중단 + 오버레이 enabled 제어
        serviceScope.launch {
            monitorUseCase.observeAndControl().collect { isVideoCall ->
                Log.d(TAG, "영상통화 상태: $isVideoCall")
                if (isVideoCall) {
                    warningOverlayManager.setEnabled(true)
                    monitorUseCase.startDetection()
                    startForegroundAppMonitoring()
                } else {
                    stopForegroundAppMonitoring()
                    stopFinancialAppMonitoring()
                    alertSoundManager.stopAlert()
                    warningOverlayManager.setEnabled(false)
                    financialBlockOverlayManager.setEnabled(false)
                    monitorUseCase.stopDetection()
                    warningOverlayManager.dismissWarning()
                    financialBlockOverlayManager.dismiss()
                    wasOverlayShowing = false
                    isDeepfakeDetected = false
                }
            }
        }

        // 감지 결과 관찰 → 오버레이 표시/숨김 + 금융 앱 차단 제어 + GPS 전송 시작
        serviceScope.launch {
            detectUseCase.observeResults().collect { result ->
                Log.d(TAG, "📊 감지 결과: $result")
                val roiBitmap = if (result.detected && result.hasRoi) {
                    cropRoiBitmap(result)
                } else null
                warningOverlayManager.onDetectionResult(result, roiBitmap)

                // 딥페이크 감지 상태에 따라 경고음 + 금융 앱 차단 오버레이 제어
                if (result.detected) {
                    isDeepfakeDetected = true
                    alertSoundManager.startAlert()
                    financialBlockOverlayManager.setEnabled(true)
                    startFinancialAppMonitoring()
                    startGpsSendingIfEligible()
                } else {
                    isDeepfakeDetected = false
                    alertSoundManager.stopAlert()
                    financialBlockOverlayManager.setEnabled(false)
                    financialBlockOverlayManager.dismiss()
                    stopFinancialAppMonitoring()
                }
            }
        }
    }

    // ── 포그라운드 앱 모니터링 ──

    /**
     * 영상통화 감지 후, 포그라운드 앱 변경을 관찰하여
     * 영상통화 화면을 벗어나면 오버레이를 숨기고,
     * 다시 돌아오면 오버레이를 복원한다.
     */
    private fun startForegroundAppMonitoring() {
        // 이미 모니터링 중이면 무시
        if (foregroundMonitorJob?.isActive == true) return

        if (!ForegroundAppMonitorService.isRunning()) {
            Log.w(TAG, "⚠️ AccessibilityService(ForegroundAppMonitor) 미활성 — 포그라운드 앱 추적 불가")
            return
        }

        Log.d(TAG, "포그라운드 앱 모니터링 시작")
        foregroundMonitorJob = serviceScope.launch {
            videoCallRepository.observeForegroundIsVideoCallApp().collect { isOnVideoCallScreen ->
                Log.d(TAG, "영상통화 화면 여부: $isOnVideoCallScreen, 오버레이 표시 중: ${warningOverlayManager.isShowing()}")

                if (isOnVideoCallScreen) {
                    // 영상통화 화면으로 복귀 → 오버레이가 이전에 표시 중이었으면 복원
                    if (wasOverlayShowing && !warningOverlayManager.isShowing()) {
                        Log.d(TAG, "📱 영상통화 화면 복귀 → 오버레이 복원")
                        warningOverlayManager.setEnabled(true)
                        warningOverlayManager.showWarning()
                    }
                } else {
                    // 영상통화 화면 이탈 → 오버레이 숨김
                    if (warningOverlayManager.isShowing()) {
                        wasOverlayShowing = true
                        Log.d(TAG, "📱 영상통화 화면 이탈 → 오버레이 숨김")
                        warningOverlayManager.dismissWarning()
                        warningOverlayManager.setEnabled(false)
                    }
                }
            }
        }
    }

    private fun stopForegroundAppMonitoring() {
        foregroundMonitorJob?.cancel()
        foregroundMonitorJob = null
        Log.d(TAG, "포그라운드 앱 모니터링 중단")
    }

    // ── 금융 앱 포그라운드 모니터링 ──

    /**
     * 딥페이크 감지 후, 금융 앱이 포그라운드에 오면
     * 금융 앱 접근 제한 오버레이를 표시/숨김한다.
     */
    private fun startFinancialAppMonitoring() {
        if (financialAppMonitorJob?.isActive == true) return

        if (!ForegroundAppMonitorService.isRunning()) {
            Log.w(TAG, "⚠️ AccessibilityService 미활성 — 금융 앱 추적 불가")
            return
        }

        Log.d(TAG, "금융 앱 모니터링 시작")
        financialAppMonitorJob = serviceScope.launch {
            videoCallRepository.observeForegroundIsFinancialApp().collect { isFinancialApp ->
                Log.d(TAG, "금융 앱 여부: $isFinancialApp, 딥페이크 감지: $isDeepfakeDetected")

                if (isFinancialApp && isDeepfakeDetected) {
                    if (!financialBlockOverlayManager.isShowing()) {
                        Log.d(TAG, "🏦 금융 앱 감지 → 접근 제한 오버레이 표시")
                        financialBlockOverlayManager.show()
                    }
                } else {
                    if (financialBlockOverlayManager.isShowing()) {
                        Log.d(TAG, "🏦 금융 앱 이탈 → 접근 제한 오버레이 해제")
                        financialBlockOverlayManager.dismiss()
                    }
                }
            }
        }
    }

    private fun stopFinancialAppMonitoring() {
        financialAppMonitorJob?.cancel()
        financialAppMonitorJob = null
        Log.d(TAG, "금융 앱 모니터링 중단")
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

        stopForegroundAppMonitoring()
        stopFinancialAppMonitoring()
        alertSoundManager.stopAlert()
        gpsSendingJob?.cancel()

        serviceScope.cancel()
        detectionRepository.stopDetection()
        videoCallRepository.stopMonitoring()
        detectionRepository.releaseMediaProjection()
        warningOverlayManager.setEnabled(false)
        warningOverlayManager.dismissWarning()
        financialBlockOverlayManager.setEnabled(false)
        financialBlockOverlayManager.dismiss()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── GPS 전송 ──

    /**
     * 딥페이크 감지 시 호출.
     * GPS 공유 토글이 ON이고 아직 활성 세션이 없을 때만 30분 전송 세션을 시작한다.
     *
     * 감지 시점에 토글이 OFF이면 세션을 시작하지 않으며,
     * 이후 토글을 ON으로 켜도 해당 감지에 대한 전송은 시작되지 않는다.
     */
    private fun startGpsSendingIfEligible() {
        if (!tokenManager.getGpsSharing()) return

        val now = System.currentTimeMillis()
        val sessionStart = gpsSessionStartedAt

        // 이미 활성 세션이 있으면 중복 시작 방지
        if (sessionStart != null && now - sessionStart < GPS_SHARING_DURATION_MS) return

        gpsSessionStartedAt = now
        gpsSendingJob?.cancel()

        Log.d(TAG, "GPS 전송 세션 시작")

        gpsSendingJob = serviceScope.launch {
            val endTime = now + GPS_SHARING_DURATION_MS

            while (isActive && System.currentTimeMillis() < endTime) {
                if (!tokenManager.getGpsSharing()) {
                    Log.d(TAG, "GPS 공유 토글 OFF — 전송 중단")
                    gpsSessionStartedAt = null
                    break
                }

                try {
                    val location = getCurrentLocation()
                    if (location != null) {
                        gpsRepository.postGps(location.latitude, location.longitude)
                        Log.d(TAG, "GPS 전송: ${location.latitude}, ${location.longitude}")
                    } else {
                        Log.w(TAG, "위치 획득 실패")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "GPS 전송 오류", e)
                }

                delay(GPS_SEND_INTERVAL_MS)
            }

            if (gpsSessionStartedAt == now) gpsSessionStartedAt = null
            Log.d(TAG, "GPS 전송 세션 종료")
        }
    }

    /**
     * 현재 위치를 단발성으로 가져온다.
     * GPS → Network 순으로 provider를 시도하며, 30초 이내 캐시가 있으면 즉시 반환한다.
     */
    @SuppressLint("MissingPermission")
    private suspend fun getCurrentLocation(): Location? = suspendCancellableCoroutine { cont ->
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

        val cached = sequenceOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER
        ).mapNotNull { provider ->
            runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
        }.firstOrNull { System.currentTimeMillis() - it.time < 30_000L }

        if (cached != null) {
            cont.resume(cached)
            return@suspendCancellableCoroutine
        }

        val provider = when {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ->
                LocationManager.GPS_PROVIDER
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ->
                LocationManager.NETWORK_PROVIDER
            else -> {
                cont.resume(null)
                return@suspendCancellableCoroutine
            }
        }

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (cont.isActive) cont.resume(location)
                locationManager.removeUpdates(this)
            }
        }

        try {
            locationManager.requestSingleUpdate(provider, listener, mainLooper)
            cont.invokeOnCancellation { locationManager.removeUpdates(listener) }
        } catch (e: SecurityException) {
            Log.e(TAG, "위치 권한 없음", e)
            cont.resume(null)
        }
    }

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

    @SuppressLint("InlinedApi", "NewApi")
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
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else {
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
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

        /** GPS 전송 주기: 10초 */
        private const val GPS_SEND_INTERVAL_MS = 10_000L

        /** GPS 전송 세션 지속 시간: 30분 */
        private const val GPS_SHARING_DURATION_MS = 30 * 60 * 1000L

        //private const val GPS_SHARING_DURATION_MS = 1 * 60 * 1000L

        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_PROJECTION_DATA = "extra_projection_data"
    }
}