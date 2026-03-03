package com.s14p21a404.shutter.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.os.IBinder
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.s14p21a404.shutter.ui.WarningDialogActivity
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 영상통화 감지 Foreground Service
 *
 * 동작 원리:
 * 1. TelephonyCallback으로 통화 상태(OFFHOOK) 감지
 * 2. CameraManager.AvailabilityCallback으로 카메라 사용 감지
 * 3. 통화 중 + 카메라 사용 중 → 영상통화로 판단 → 경고 다이얼로그 표시
 */
class VideoCallDetectService : Service() {

    private lateinit var telephonyManager: TelephonyManager
    private lateinit var cameraManager: CameraManager

    @Volatile
    private var callState: Int = TelephonyManager.CALL_STATE_IDLE

    @Volatile
    private var isCameraBusy: Boolean = false

    private val dialogShown = AtomicBoolean(false)

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

        startAsForeground()
        registerCallbacks()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "서비스 종료")
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
            if (dialogShown.compareAndSet(false, true)) {
                Log.d(TAG, "🚨 영상통화 감지 → 경고 다이얼로그 표시")
                showWarningDialog()
            }
        } else {
            // 통화 종료되면 다음 감지를 위해 초기화
            dialogShown.set(false)
        }
    }

    /**
     * 경고 다이얼로그 Activity 실행
     */
    private fun showWarningDialog() {
        val intent = Intent(this, WarningDialogActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
    }

    // ── Foreground Service 설정 ──

    private fun startAsForeground() {
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

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
    }

    companion object {
        private const val TAG = "VideoCallDetectService"
        private const val NOTIFICATION_ID = 1001
    }
}
