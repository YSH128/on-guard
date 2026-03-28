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
import java.time.LocalDateTime
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
    private lateinit var deepfakeAlertSender: DeepfakeAlertSender
    private lateinit var alertRepository: com.s14p21a404.shutter.domain.repository.AlertRepository
    private lateinit var gpsRepository: GpsRepository
    private lateinit var tokenManager: TokenManager

    // GPS 전송 세션 관리
    private var gpsSendingJob: Job? = null
    private var gpsSessionStartedAt: Long? = null

    /** 포그라운드 앱 모니터링 Job — 영상통화 감지 시 시작, 종료 시 취소 */
    private var foregroundMonitorJob: Job? = null

    /** 금융 앱 포그라운드 모니터링 Job — 딥페이크 감지 시 시작, 종료 시 취소 */
    private var financialAppMonitorJob: Job? = null

    /** 비딥페이크 사이클 후 20초 대기 후 재시작하는 Job */
    private var cooldownJob: Job? = null

    /**
     * 딥페이크가 감지되었는지 여부.
     * 금융 앱 차단 오버레이는 딥페이크 감지 + 금융 앱 포그라운드 시에만 표시한다.
     */
    @Volatile
    private var isDeepfakeDetected = false

    /** 현재 통화 세션에서 보호자 카카오톡 알림을 이미 발송했는지 여부. */
    @Volatile
    private var alertSentForCurrentSession = false

    /**
     * 오버레이가 표시 중이었는지 여부.
     * 영상통화 화면을 벗어났다가 돌아올 때 오버레이를 복원하기 위해 사용.
     */
    @Volatile
    private var wasOverlayShowing = false

    /** 현재 영상통화 세션이 활성 중인지 — 통화 종료 후 도착하는 잔존 결과를 필터링한다. */
    @Volatile
    private var isVideoCallSessionActive = false

    // ── 생명주기 ──

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        Log.d(TAG, "서비스 생성")

        val locator = ServiceLocator.getInstance(this)
        monitorUseCase = locator.monitorVideoCallUseCase
        detectUseCase = locator.detectDeepfakeUseCase
        warningOverlayManager = locator.warningOverlayManager
        financialBlockOverlayManager = locator.financialBlockOverlayManager
        alertSoundManager = locator.alertSoundManager
        videoCallRepository = locator.videoCallRepository
        detectionRepository = locator.detectionRepository
        deepfakeAlertSender = locator.deepfakeAlertSender
        alertRepository = locator.alertRepository
        gpsRepository = locator.gpsRepository
        tokenManager = locator.tokenManager

        // 오버레이 매니저에 레포지토리 주입 (OpenCV 강제 활성 제어용)
        warningOverlayManager.deepfakeDetectionRepository = detectionRepository

        startAsForeground()

        // 영상통화 모니터링 시작
        videoCallRepository.startMonitoring()

        // 오버레이 콜백 설정
        warningOverlayManager.onEndCall = {
            Log.d(TAG, "📞 전화 종료 요청 (오버레이)")

            if (videoCallRepository.isInAppCallActive()) {
                // ── 인앱(Agora) 영상통화 종료 ──
                Log.d(TAG, "📞 인앱 영상통화 → onInAppCallEnd 콜백 호출")
                onInAppCallEnd?.invoke()
                videoCallRepository.setInAppCallActive(false)
            } else {
                // ── 외부 앱(카카오톡/통신사) 영상통화 종료 ──
                val service = ForegroundAppMonitorService.instance
                if (service != null) {
                    val success = service.performEndCall()
                    Log.d(TAG, "📞 AccessibilityService 통화 종료 결과: $success")
                } else {
                    Log.w(TAG, "❌ AccessibilityService 미활성 — 통화 종료 불가")
                }
            }
            warningOverlayManager.dismissAll()
            alertSoundManager.stopAlert()

            // ── 세션 플래그 리셋 (재통화 시 정상 동작 보장) ──
            isVideoCallSessionActive = false
            cooldownJob?.cancel()
            cooldownJob = null
            isDeepfakeDetected = false
            alertSentForCurrentSession = false
            wasOverlayShowing = false
            financialBlockOverlayManager.setEnabled(false)
            financialBlockOverlayManager.dismiss()
            stopFinancialAppMonitoring()
        }
        warningOverlayManager.onRescan = {
            Log.d(TAG, "🔄 재탐지 요청 (오버레이)")
            cooldownJob?.cancel()
            cooldownJob = null
            warningOverlayManager.dismissAll()
            isDeepfakeDetected = false
            alertSentForCurrentSession = false
            alertSoundManager.stopAlert()
            financialBlockOverlayManager.setEnabled(false)
            financialBlockOverlayManager.dismiss()
            // 스캐닝 pill 다시 표시 + 새 사이클 시작
            warningOverlayManager.showScanningPill()
            monitorUseCase.startDetection()
        }


        // 영상통화 상태 관찰 → 캡처·감지 자동 시작/중단 + 오버레이 enabled 제어
        serviceScope.launch {
            monitorUseCase.observeAndControl().collect { isVideoCall ->
                Log.d(TAG, "영상통화 상태: $isVideoCall")
                if (isVideoCall) {
                    // ── 새 영상통화 세션 시작 — 이전 세션 플래그 초기화 ──
                    isVideoCallSessionActive = true
                    cooldownJob?.cancel()
                    cooldownJob = null
                    isDeepfakeDetected = false
                    alertSentForCurrentSession = false
                    // 주의: wasOverlayShowing은 여기서 리셋하지 않는다.
                    // 홈화면 이탈 후 복귀 시 오버레이 복원에 사용되기 때문.
                    // 통화 종료(false) 또는 onEndCall에서만 리셋한다.

                    warningOverlayManager.setEnabled(true)
                    warningOverlayManager.showScanningPill() // 스캐닝 pill 표시
                    monitorUseCase.startDetection()
                    startForegroundAppMonitoring()
                } else {
                    isVideoCallSessionActive = false
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
                    alertSentForCurrentSession = false
                }
            }
        }

        // 사이클(10프레임) 집계 결과 관찰 → 딥페이크 판정 + 쿨다운 제어
        serviceScope.launch {
            detectUseCase.observeResults().collect { result ->
                Log.d(TAG, "📊 사이클 결과: $result")

                // 통화 종료 후 캡처 스레드에서 늦게 도착한 잔존 결과는 무시한다.
                if (!isVideoCallSessionActive) {
                    Log.d(TAG, "⚠️ 영상통화 세션 비활성 — 잔존 사이클 결과 무시")
                    return@collect
                }

                // 이미 딥페이크 확정된 세션 → RoI는 프레임 결과로 실시간 갱신하므로 여기서는 스킵
                if (isDeepfakeDetected) return@collect

                val roiBitmap = if (result.detected && result.hasRoi) {
                    cropRoiBitmap(result)
                } else null
                warningOverlayManager.onDetectionResult(result, roiBitmap)

                if (result.detected) {
                    // ── 딥페이크 최초 감지 ──
                    isDeepfakeDetected = true
                    //
                    // rmsep sork alertSoundManager.startAlert()
                    financialBlockOverlayManager.setEnabled(true)
                    startFinancialAppMonitoring()
                    if (!alertSentForCurrentSession) {
                        alertSentForCurrentSession = true
                        serviceScope.launch(Dispatchers.Main) {
                            Log.d(TAG, "딥페이크 최초 감지 — 보호자 카카오톡 알림 발송 시작")
                            val analysisTags = warningOverlayManager.getFormattedGmsTags()
                            deepfakeAlertSender.sendAlert(LocalDateTime.now(), analysisTags)
                        }
                        // 인앱 영상통화 중이면 상대방 GPS 요청 콜백 호출
                        onInAppDeepfakeDetected?.let {
                            Log.d(TAG, "딥페이크 최초 감지 — 인앱 통화 GPS 요청 콜백 호출")
                            serviceScope.launch(Dispatchers.Main) { it() }
                        }
                    }
                    startGpsSendingIfEligible()
                } else {
                    // ── 비딥페이크 사이클 완료 → 20초 후 재시작 ──
                    Log.d(TAG, "비딥페이크 사이클 완료 — 20초 후 재시작 예정")
                    monitorUseCase.stopDetection()
                    cooldownJob?.cancel()
                    cooldownJob = serviceScope.launch {
                        delay(RESCAN_COOLDOWN_MS)
                        if (!isDeepfakeDetected) {
                            Log.d(TAG, "쿨다운 종료 — 새 사이클 시작")
                            warningOverlayManager.showScanningPill()
                            monitorUseCase.startDetection()
                        }
                    }
                }
            }
        }

        // 프레임별 실시간 결과 관찰 → ROI + fakeProb 매 프레임 갱신
        serviceScope.launch {
            detectUseCase.observeFrameResults().collect { frameResult ->
                // 통화 종료 후 캡처 스레드에서 늦게 도착한 잔존 프레임 결과 무시
                if (!isVideoCallSessionActive) return@collect
                // ROI 박스 위치 및 fakeProb — 박스가 존재할 때만 내부에서 동작하므로 항상 호출 가능
                warningOverlayManager.updateRoiRealtime(frameResult)
                if (isDeepfakeDetected) {
                    warningOverlayManager.onDetectionResult(frameResult)
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
            if (!videoCallRepository.isInAppCallActive()) return
        }

        Log.d(TAG, "포그라운드 앱 모니터링 시작 (인앱=${videoCallRepository.isInAppCallActive()})")
        foregroundMonitorJob = serviceScope.launch {
            videoCallRepository.observeForegroundIsVideoCallApp().collect { isOnVideoCallScreen ->
                Log.d(TAG, "영상통화 화면 여부: $isOnVideoCallScreen, 오버레이 표시 중: ${warningOverlayManager.isShowing()}, 딥페이크감지=$isDeepfakeDetected")

                if (isOnVideoCallScreen) {
                    // ── 영상통화 화면으로 복귀 ──
                    if (wasOverlayShowing) {
                        Log.d(TAG, "📱 영상통화 화면 복귀 → 오버레이 복원 → 추론 재시작")

                        // 오버레이 활성화
                        warningOverlayManager.setEnabled(true)

                        if (isDeepfakeDetected) {
                            // 딥페이크 확정 상태에서 복귀 → 경고 오버레이 먼저 복원 후 추론 재시작
                            warningOverlayManager.showWarning()
                        } else {
                            // 아직 딥페이크 미확정 → 스캐닝 pill 먼저 표시 후 추론 시작
                            warningOverlayManager.showScanningPill()
                        }

                        // 오버레이가 완전히 렌더링된 후 추론 재시작 (500ms 딜레이)
                        delay(500)
                        monitorUseCase.startDetection()
                        Log.d(TAG, "📱 추론 재시작 완료")
                    }
                } else {
                    // ── 영상통화 화면 이탈 ──
                    Log.d(TAG, "📱 영상통화 화면 이탈 → 추론 중단 + 오버레이 숨김")

                    // 현재 오버레이가 표시 중이었거나 딥페이크 감지 상태라면 복귀 시 복원할 수 있도록 플래그 세팅
                    if (warningOverlayManager.isShowing() || isDeepfakeDetected) {
                        wasOverlayShowing = true
                    }

                    // 추론 중단 (화면 캡처 + 모델 추론 모두 멈춤)
                    monitorUseCase.stopDetection()
                    cooldownJob?.cancel()
                    cooldownJob = null

                    // 오버레이 숨김 (세션 상태는 보존 — 복귀 시 복원용)
                    warningOverlayManager.hideOverlaysKeepState()
                    warningOverlayManager.setEnabled(false)
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
            if (!videoCallRepository.isInAppCallActive()) return
        }

        Log.d(TAG, "금융 앱 모니터링 시작")
        financialAppMonitorJob = serviceScope.launch {
            videoCallRepository.observeForegroundIsFinancialApp().collect { isFinancialApp ->
                Log.d(TAG, "▶ [FinancialMonitor] isFinancialApp=$isFinancialApp, isDeepfake=$isDeepfakeDetected, isShowing=${financialBlockOverlayManager.isShowing()}")

                if (isFinancialApp && isDeepfakeDetected) {
                    if (!financialBlockOverlayManager.isShowing()) {
                        Log.d(TAG, "🏦 [FinancialMonitor] 금융 앱 진입 감지 → 접근 제한 오버레이 표시")
                        financialBlockOverlayManager.show(isInApp = videoCallRepository.isInAppCallActive())
                    }
                } else {
                    if (financialBlockOverlayManager.isShowing()) {
                        Log.d(TAG, "🏦 [FinancialMonitor] 금융 앱 이탈 감지 (또는 딥페이크 해제) → 접근 제한 오버레이 해제")
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
        isRunning = false
        super.onDestroy()
        Log.d(TAG, "서비스 종료")

        stopForegroundAppMonitoring()
        stopFinancialAppMonitoring()
        alertSoundManager.stopAlert()
        gpsSendingJob?.cancel()
        cooldownJob?.cancel()

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
        serviceScope.launch {
            alertRepository.sendLocationAlert()
                .onSuccess { Log.d(TAG, "위치 공유 알림 전송 성공") }
                .onFailure { Log.e(TAG, "위치 공유 알림 전송 실패", it) }
        }

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

        // Network를 먼저 시도 — WiFi/셀 기반이라 실내에서도 빠르게 반환됨
        val cached = sequenceOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER
        ).mapNotNull { provider ->
            runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
        }.firstOrNull { System.currentTimeMillis() - it.time < 30_000L }

        if (cached != null) {
            cont.resume(cached)
            return@suspendCancellableCoroutine
        }

        val provider = when {
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ->
                LocationManager.NETWORK_PROVIDER
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ->
                LocationManager.GPS_PROVIDER
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
            // 10초 안에 위치 못 받으면 null 반환
            val timeoutHandler = android.os.Handler(mainLooper)
            val timeoutRunnable = Runnable {
                if (cont.isActive) {
                    locationManager.removeUpdates(listener)
                    cont.resume(null)
                }
            }
            timeoutHandler.postDelayed(timeoutRunnable, 10_000L)
            cont.invokeOnCancellation {
                locationManager.removeUpdates(listener)
                timeoutHandler.removeCallbacks(timeoutRunnable)
            }
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
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else {
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
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

        /** 서비스 실제 실행 여부 — 프로세스 재시작 후에도 정확히 반영됨 */
        @Volatile
        var isRunning: Boolean = false
        private const val NOTIFICATION_ID = 1001

        /** 비딥페이크 사이클 후 재탐지까지 대기 시간: 20초 */
        private const val RESCAN_COOLDOWN_MS = 20_000L

        /** GPS 전송 주기: 10초 */
        private const val GPS_SEND_INTERVAL_MS = 10_000L

        /** GPS 전송 세션 지속 시간: 30분 */
        private const val GPS_SHARING_DURATION_MS = 30 * 60 * 1000L

        //private const val GPS_SHARING_DURATION_MS = 1 * 60 * 1000L

        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_PROJECTION_DATA = "extra_projection_data"

        /**
         * 인앱(Agora) 영상통화 종료 콜백.
         * VideoCallScreen에서 등록하고, 통화 종료 시 해제한다.
         * 오버레이 "전화 종료" 버튼 클릭 시 인앱 통화이면 이 콜백을 호출한다.
         */
        @Volatile
        var onInAppCallEnd: (() -> Unit)? = null

        /**
         * 인앱 영상통화 중 딥페이크/딥보이스 최초 감지 시 호출된다.
         * VideoCallScreen에서 등록하고 상대방 GPS 요청에 활용한다.
         */
        @Volatile
        var onInAppDeepfakeDetected: (() -> Unit)? = null
    }
}