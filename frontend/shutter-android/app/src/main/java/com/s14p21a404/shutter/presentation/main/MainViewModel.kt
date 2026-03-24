package com.s14p21a404.shutter.presentation.main

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.s14p21a404.shutter.di.ServiceLocator
import com.s14p21a404.shutter.presentation.service.ForegroundAppMonitorService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Main 화면 UI 상태
 */
data class MainUiState(
    val isServiceRunning: Boolean = false,
    val allPermissionsGranted: Boolean = false,
    val overlayPermissionGranted: Boolean = false,
    val accessibilityServiceEnabled: Boolean = false
)

/**
 * Main 화면 ViewModel
 *
 * 권한 상태 확인, 서비스 상태 관리를 담당한다.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val serviceLocator = ServiceLocator.getInstance(application)
    private val gpsRepository = serviceLocator.gpsRepository
    private val tokenManager = serviceLocator.tokenManager

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        checkAllPermissions()
        syncGpsSharingState()
    }

    /**
     * 앱 시작 시 GPS 공유 상태를 서버와 동기화.
     * 재설치 후 Redis에 잔여 데이터가 남아있을 경우를 대비해
     * SharedPreferences의 토글 상태를 서버에 전송한다.
     */
    private fun syncGpsSharingState() {
        viewModelScope.launch {
            val savedSharing = tokenManager.getGpsSharing()
            gpsRepository.toggleGpsSharing(savedSharing)
                .onSuccess { enabled -> tokenManager.saveGpsSharing(enabled) }
        }
    }

    /**
     * 모든 필요 권한 확인
     */
    fun checkAllPermissions() {
        val context = getApplication<Application>()

        val hasPhoneState = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        val hasNotification = ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        val hasLocation = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasAnswerPhoneCalls = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ANSWER_PHONE_CALLS
        ) == PackageManager.PERMISSION_GRANTED

        val hasOverlay = Settings.canDrawOverlays(context)

        val hasAccessibility = ForegroundAppMonitorService.isRunning()

        _uiState.update { it.copy(
            allPermissionsGranted = hasPhoneState && hasNotification && hasLocation && hasAnswerPhoneCalls,
            overlayPermissionGranted = hasOverlay,
            accessibilityServiceEnabled = hasAccessibility
        ) }

        Log.d(TAG, "권한 상태 - Phone: $hasPhoneState, Notification: $hasNotification, Location: $hasLocation, AnswerPhoneCalls: $hasAnswerPhoneCalls, Overlay: $hasOverlay, Accessibility: $hasAccessibility")
    }

    /**
     * 서비스 실행 상태 업데이트
     */
    fun setServiceRunning(running: Boolean) {
        _uiState.update { it.copy(isServiceRunning = running) }
    }

    companion object {
        private const val TAG = "MainViewModel"
    }
}

