package com.s14p21a404.shutter.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import android.provider.Settings
import com.s14p21a404.shutter.service.capture.ScreenCaptureManager
import com.s14p21a404.shutter.service.detector.BroadcastTriggerDetector
import com.s14p21a404.shutter.service.detector.DeepfakeDetector
import com.s14p21a404.shutter.service.detector.RealDeepfakeDetector
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 영상통화 감지 Foreground Service
 *
 * 동작 원리:
 * 1. TelephonyCallback으로 통화 상태(OFFHOOK) 감지
 * 2. CameraManager.AvailabilityCallback으로 카메라 사용 감지
 * 3. 통화 중 + 카메라 사용 중 → 영상통화로 판단
 * 4. DeepfakeDetector에 감지 시작 요청
 * 5. 딥페이크 감지 결과가 "위험"일 때만 경고 오버레이 표시
 */
class VideoCallDetectService : Service() {

    private lateinit var telephonyManager: TelephonyManager
    private lateinit var cameraManager: CameraManager

    @Volatile
    private var callState: Int = TelephonyManager.CALL_STATE_IDLE

    @Volatile
    private var isCameraBusy: Boolean = false

    private val detectionActive = AtomicBoolean(false)
    private lateinit var warningOverlayManager: WarningOverlayManager

    // ── 화면 캡처 ──
    private var mediaProjection: MediaProjection? = null
    private var screenCaptureManager: ScreenCaptureManager? = null

    // ── 딥페이크 감지기 ──
    // BuildConfig 등으로 Stub ↔ Real 전환
    //  - BroadcastTriggerDetector: ADB 명령으로 수동 트리거 (테스트용)
    //  - AlwaysDeepfakeDetector: 항상 딥페이크 (자동 테스트)
    //  - RealDeepfakeDetector: 실제 캡처 + JNI + C++ AI 추론
    private lateinit var detector: DeepfakeDetector

    // ── 통화 상태 콜백 ──
    private val callStateCallback = object : TelephonyCallback(),
        TelephonyCallback.CallStateListener {
        override fun onCallStateChanged(state: Int) {
            Log.d(TAG, "통화 상태 변경: $state")
            callState = state
            evaluate()
        }
    }

    // ── 카메라 사용 상태 콜백 ──
    private val cameraCallback = object : CameraManager.AvailabilityCallback() {
        override fun onCameraAvailable(cameraId: String) {
            Log.d(TAG, "카메라 사용 가능: $cameraId")
            isCameraBusy = false
            evaluate()
        }

        override fun onCameraUnavailable(cameraId: String) {
            Log.d(TAG, "카메라 사용 중: $cameraId")
            isCameraBusy = true
            evaluate()
        }
    }

    // ── 생명주기 ──

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "서비스 생성")

        telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        warningOverlayManager = WarningOverlayManager(this)
        detector = BroadcastTriggerDetector(this)      // ADB 수동 테스트용
        // detector = RealDeepfakeDetector()            // 실제 JNI 연동 (NDK 빌드 후 전환)

        startAsForeground()
        registerCallbacks()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // MainActivity에서 전달한 MediaProjection 데이터로 초기화
        if (intent != null && intent.hasExtra(EXTRA_RESULT_CODE)) {
            val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
            val projectionData = intent.getParcelableExtra(EXTRA_PROJECTION_DATA, Intent::class.java)
            if (projectionData != null) {
                // MediaProjection 동의가 있으므로 mediaProjection 타입으로 foreground 업그레이드
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
        detector.stopDetection()
        screenCaptureManager?.stopCapture()
        screenCaptureManager = null
        mediaProjection?.stop()
        mediaProjection = null
        warningOverlayManager.dismiss()
        unregisterCallbacks()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── 콜백 등록/해제 ──

    private fun registerCallbacks() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
            == PackageManager.PERMISSION_GRANTED
        ) {
            telephonyManager.registerTelephonyCallback(mainExecutor, callStateCallback)
            Log.d(TAG, "TelephonyCallback 등록")
        } else {
            Log.w(TAG, "READ_PHONE_STATE 권한 없음")
        }

        cameraManager.registerAvailabilityCallback(cameraCallback, null)
        Log.d(TAG, "CameraCallback 등록")
    }

    private fun unregisterCallbacks() {
        try {
            telephonyManager.unregisterTelephonyCallback(callStateCallback)
        } catch (e: Exception) {
            Log.e(TAG, "TelephonyCallback 해제 실패", e)
        }
        try {
            cameraManager.unregisterAvailabilityCallback(cameraCallback)
        } catch (e: Exception) {
            Log.e(TAG, "CameraCallback 해제 실패", e)
        }
    }

    // ── 영상통화 판단 ──

    private fun evaluate() {
        val isOffhook = callState == TelephonyManager.CALL_STATE_OFFHOOK
        val isVideoCall = isOffhook && isCameraBusy

        if (isVideoCall) {
            if (detectionActive.compareAndSet(false, true)) {
                Log.d(TAG, "🎥 영상통화 감지 → 화면 캡처 + 딥페이크 감지 시작")

                // 화면 캡처 시작 (MediaProjection이 있을 때만)
                startScreenCapture()

                // 딥페이크 감지 시작
                detector.startDetection { result ->
                    Log.d(TAG, "📊 감지 결과: detected=${result.detected}, roi=${result.roi?.contentToString()}")
                    if (result.detected) {
                        showWarningDialog()
                    } else {
                        warningOverlayManager.dismiss()
                    }
                }
            }
        } else {
            if (detectionActive.compareAndSet(true, false)) {
                Log.d(TAG, "📴 통화 종료 → 화면 캡처 + 딥페이크 감지 중단")
                screenCaptureManager?.stopCapture()
                screenCaptureManager = null
                detector.stopDetection()
                warningOverlayManager.dismiss()
            }
        }
    }

    /**
     * 화면 캡처 시작
     * MediaProjection이 있을 때만 동작. 없으면 로그만 남기고 스킵.
     */
    private fun startScreenCapture() {
        val projection = mediaProjection
        if (projection == null) {
            Log.w(TAG, "MediaProjection 없음 — 화면 캡처 불가 (감지는 Stub으로 동작)")
            return
        }

        screenCaptureManager = ScreenCaptureManager(
            context = this,
            mediaProjection = projection,
            onFrameCaptured = { buffer, width, height, rowStride, _ ->
                Log.d(TAG, "프레임 캡처됨: ${width}x${height}, buffer=${buffer.size}bytes")

                // 캡처된 프레임을 RealDeepfakeDetector → JNI로 전달
                (detector as? RealDeepfakeDetector)?.feedFrame(buffer, width, height, rowStride)

                // 디버그: 캡처된 프레임을 파일로 저장하여 확인 (필요 시 주석 해제)
//                 bitmap?.let { bmp ->
//                     try {
//                         val file = java.io.File(cacheDir, "captured_frame.jpg")
//                         java.io.FileOutputStream(file).use { fos ->
//                             bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, fos)
//                         }
//                         Log.d(TAG, "프레임 저장됨: ${file.absolutePath}")
//                     } catch (e: Exception) {
//                         Log.e(TAG, "프레임 저장 실패", e)
//                     }
//                 }
            }
        )
        screenCaptureManager?.startCapture()
    }

    /**
     * 경고 오버레이 표시 (WindowManager.addView)
     */
    private fun showWarningDialog() {
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "오버레이 권한 없음 — 경고 표시 불가")
            return
        }
        warningOverlayManager.show()
    }

    // ── Foreground Service 설정 ──

    /**
     * @param includeMediaProjection MediaProjection 동의가 있을 때만 true.
     *   서비스 재시작(크래시 복구) 시에는 동의가 없으므로 false로 시작해야 SecurityException 방지.
     */
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
