package com.s14p21a404.shutter.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.s14p21a404.shutter.domain.repository.VideoCallRepository
import com.s14p21a404.shutter.presentation.service.ForegroundAppMonitorService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * 영상통화 상태 Repository 구현체
 *
 * TelephonyCallback + CameraManager.AvailabilityCallback으로
 * 통화 중 + 카메라 사용 중 → 영상통화 상태를 판단한다.
 *
 * AccessibilityService로부터 포그라운드 패키지를 받아
 * 영상통화 앱 화면 위에 있는지 여부도 판단한다.
 */
class VideoCallRepositoryImpl(private val context: Context) : VideoCallRepository {

    private val telephonyManager =
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private val cameraManager =
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private val _videoCallState = MutableStateFlow(false)

    @Volatile
    private var callState: Int = TelephonyManager.CALL_STATE_IDLE

    @Volatile
    private var isCameraBusy: Boolean = false

    /**
     * 영상통화가 시작되었을 때 기록된 패키지명.
     * 이 패키지가 포그라운드이면 오버레이 표시, 아니면 숨김.
     */
    @Volatile
    private var videoCallPackage: String? = null

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

    override fun observeVideoCallState(): Flow<Boolean> = _videoCallState.asStateFlow()

    override fun observeForegroundIsVideoCallApp(): Flow<Boolean> {
        return ForegroundAppMonitorService.foregroundPackage.map { currentPkg ->
            val targetPkg = videoCallPackage
            if (targetPkg == null) {
                // 영상통화 패키지가 설정되지 않았으면 true (기본 표시)
                true
            } else {
                // 포그라운드 패키지가 영상통화 패키지와 같거나,
                // 시스템 전화 UI 패키지인 경우 true
                val isOnVideoCallScreen = currentPkg == targetPkg
                        || currentPkg in VIDEO_CALL_RELATED_PACKAGES
                        || currentPkg.isEmpty()  // 아직 감지 전
                Log.d(TAG, "포그라운드=$currentPkg, 영상통화앱=$targetPkg → onScreen=$isOnVideoCallScreen")
                isOnVideoCallScreen
            }
        }
    }

    override fun setVideoCallPackage(packageName: String?) {
        videoCallPackage = packageName
        Log.d(TAG, "영상통화 패키지 설정: $packageName")
    }

    override fun startMonitoring() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
            == PackageManager.PERMISSION_GRANTED
        ) {
            telephonyManager.registerTelephonyCallback(context.mainExecutor, callStateCallback)
            Log.d(TAG, "TelephonyCallback 등록")
        } else {
            Log.w(TAG, "READ_PHONE_STATE 권한 없음")
        }

        cameraManager.registerAvailabilityCallback(cameraCallback, null)
        Log.d(TAG, "CameraCallback 등록")
    }

    override fun stopMonitoring() {
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
        _videoCallState.value = false
        videoCallPackage = null
    }

    private fun evaluate() {
        val isOffhook = callState == TelephonyManager.CALL_STATE_OFFHOOK
        val isVideoCall = isOffhook && isCameraBusy
        _videoCallState.value = isVideoCall

        // 영상통화 감지 시 현재 포그라운드 앱을 영상통화 패키지로 기록
        if (isVideoCall && videoCallPackage == null) {
            val currentFg = ForegroundAppMonitorService.foregroundPackage.value
            if (currentFg.isNotEmpty()) {
                videoCallPackage = currentFg
                Log.d(TAG, "영상통화 시작 시 포그라운드 패키지 자동 기록: $currentFg")
            }
        } else if (!isVideoCall) {
            videoCallPackage = null
        }

        Log.d(TAG, "영상통화 판단: offhook=$isOffhook, camera=$isCameraBusy → videoCall=$isVideoCall")
    }

    companion object {
        private const val TAG = "VideoCallRepo"

        /**
         * 시스템 전화 UI 패키지들.
         * 영상통화 중 시스템 InCallUI가 포그라운드인 경우에도
         * 영상통화 화면으로 간주한다.
         *
         * 주의: 카카오톡, WhatsApp 등 일반 앱은 여기에 넣지 않는다.
         * 영상통화를 건 앱은 evaluate() 시점에 videoCallPackage로 자동 기록되므로
         * 별도 등록 불필요. 여기에 넣으면 일반 채팅할 때도 오버레이가 뜬다.
         */
        private val VIDEO_CALL_RELATED_PACKAGES = setOf(
            "com.samsung.android.incallui",         // 삼성 전화 UI
            "com.android.incallui",                  // AOSP 전화 UI
            "com.google.android.dialer",             // Google 전화 앱
            "com.samsung.android.dialer",            // 삼성 전화 앱
            "com.android.dialer",                    // AOSP 전화 앱
            "com.samsung.android.vtcallplugin",      // 삼성 영상통화 플러그인
        )
    }
}



