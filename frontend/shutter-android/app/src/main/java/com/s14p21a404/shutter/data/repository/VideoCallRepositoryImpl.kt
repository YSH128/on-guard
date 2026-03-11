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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 영상통화 상태 Repository 구현체
 *
 * TelephonyCallback + CameraManager.AvailabilityCallback으로
 * 통화 중 + 카메라 사용 중 → 영상통화 상태를 판단한다.
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
    }

    private fun evaluate() {
        val isOffhook = callState == TelephonyManager.CALL_STATE_OFFHOOK
        val isVideoCall = isOffhook && isCameraBusy
        _videoCallState.value = isVideoCall
        Log.d(TAG, "영상통화 판단: offhook=$isOffhook, camera=$isCameraBusy → videoCall=$isVideoCall")
    }

    companion object {
        private const val TAG = "VideoCallRepo"
    }
}

