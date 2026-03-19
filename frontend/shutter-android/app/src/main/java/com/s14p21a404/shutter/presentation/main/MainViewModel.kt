package com.s14p21a404.shutter.presentation.main

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import com.s14p21a404.shutter.presentation.service.ForegroundAppMonitorService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        checkAllPermissions()
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

        val hasOverlay = Settings.canDrawOverlays(context)

        val hasAccessibility = ForegroundAppMonitorService.isRunning()

        _uiState.update { it.copy(
            allPermissionsGranted = hasPhoneState && hasNotification && hasLocation,
            overlayPermissionGranted = hasOverlay,
            accessibilityServiceEnabled = hasAccessibility
        ) }

        Log.d(TAG, "권한 상태 - Phone: $hasPhoneState, Notification: $hasNotification, Location: $hasLocation, Overlay: $hasOverlay, Accessibility: $hasAccessibility")
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

