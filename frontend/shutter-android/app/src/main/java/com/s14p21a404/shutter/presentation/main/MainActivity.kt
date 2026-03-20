package com.s14p21a404.shutter.presentation.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.s14p21a404.shutter.presentation.login.LoginScreen
import com.s14p21a404.shutter.presentation.login.LoginUiState
import com.s14p21a404.shutter.presentation.login.LoginViewModel
import com.s14p21a404.shutter.presentation.service.ForegroundAppMonitorService
import com.s14p21a404.shutter.presentation.service.VideoCallDetectService
import com.s14p21a404.shutter.ui.theme.ShutterandroidTheme

class MainActivity : ComponentActivity() {

    private val loginViewModel: LoginViewModel by viewModels()
    private val viewModel: MainViewModel by viewModels()

    // 전화 상태 권한 런처
    private val phoneStateLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Log.d(TAG, "전화 상태 권한 결과: $granted")
        viewModel.checkAllPermissions()
    }

    // 알림 권한 런처
    private val notificationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Log.d(TAG, "알림 권한 결과: $granted")
        viewModel.checkAllPermissions()
    }

    // 위치 권한 런처
    private val locationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Log.d(TAG, "위치 권한 결과: $granted")
        viewModel.checkAllPermissions()
    }

    // 화면 캡처 동의 런처
    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            Log.d(TAG, "화면 캡처 동의 완료")
            startServiceWithProjection(result.resultCode, result.data!!)
        } else {
            Log.w(TAG, "화면 캡처 동의 거부됨")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ShutterandroidTheme {
                val loginUiState by loginViewModel.loginUiState.collectAsState()
                val loginError by loginViewModel.loginError.collectAsState()

                when (val state = loginUiState) {
                    LoginUiState.Checking -> {
                        // 로그인 상태 확인 중 — 빈 화면 유지 (스플래시 대체)
                    }
                    LoginUiState.LoggedOut -> {
                        LoginScreen(
                            onKakaoLogin = { loginViewModel.loginWithKakao(this@MainActivity) },
                            loginError = loginError,
                            onDismissError = { loginViewModel.dismissLoginError() }
                        )
                    }
                    is LoginUiState.NeedsRegistration -> {
                        // 신규 회원: 이름 입력 화면 (권한 화면에 포함)
                        val uiState by viewModel.uiState.collectAsState()
                        MainScreen(
                            uiState = uiState,
                            isNewUser = true,
                            onRegister = { name -> loginViewModel.register(state.kakaoId, name) },
                            onRequestPhoneState = { requestPhoneStatePermission() },
                            onRequestNotification = { requestNotificationPermission() },
                            onRequestLocation = { requestLocationPermission() },
                            onRequestOverlayPermission = { requestOverlayPermission() },
                            onRequestAccessibilityPermission = { requestAccessibilityPermission() },
                            onToggleService = { toggleService() },
                            onLogout = { loginViewModel.logout() },
                            loginError = loginError,
                            onDismissError = { loginViewModel.dismissLoginError() }
                        )
                    }
                    LoginUiState.LoggedIn -> {
                        val uiState by viewModel.uiState.collectAsState()
                        MainScreen(
                            uiState = uiState,
                            onRequestPhoneState = { requestPhoneStatePermission() },
                            onRequestNotification = { requestNotificationPermission() },
                            onRequestLocation = { requestLocationPermission() },
                            onRequestOverlayPermission = { requestOverlayPermission() },
                            onRequestAccessibilityPermission = { requestAccessibilityPermission() },
                            onToggleService = { toggleService() },
                            onLogout = { loginViewModel.logout() }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkAllPermissions()

        // AccessibilityService 활성 여부 확인
        if (!ForegroundAppMonitorService.isRunning()) {
            Log.w(TAG, "⚠️ AccessibilityService 미활성 — 접근성 설정에서 Shutter를 켜주세요")
        }
    }

    /**
     * 접근성 서비스 설정 화면으로 이동.
     */
    private fun requestAccessibilityPermission() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }


    /**
     * 전화 상태 권한 요청
     * "다시 묻지 않음" 선택 시 앱 설정으로 이동
     */
    private fun requestPhoneStatePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            if (!shouldShowRequestPermissionRationale(Manifest.permission.READ_PHONE_STATE)
                && viewModel.uiState.value.permissionCheckCount > 2
            ) {
                openAppSettings()
            } else {
                phoneStateLauncher.launch(Manifest.permission.READ_PHONE_STATE)
            }
        }
    }

    /**
     * 알림 권한 요청
     */
    private fun requestNotificationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            if (!shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
                && viewModel.uiState.value.permissionCheckCount > 2
            ) {
                openAppSettings()
            } else {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    /**
     * 위치 권한 요청
     */
    private fun requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            if (!shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
                && viewModel.uiState.value.permissionCheckCount > 2
            ) {
                openAppSettings()
            } else {
                locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    /**
     * 앱 상세 설정 화면으로 이동 (영구 거부된 권한 처리용)
     */
    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = "package:$packageName".toUri()
        }
        startActivity(intent)
    }

    /**
     * 오버레이 권한 요청 (설정 화면으로 이동)
     */
    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:$packageName".toUri()
            )
            startActivity(intent)
        }
    }

    /**
     * 서비스 시작/중지 토글
     */
    private fun toggleService() {
        val state = viewModel.uiState.value
        if (state.isServiceRunning) {
            stopService(Intent(this, VideoCallDetectService::class.java))
            viewModel.setServiceRunning(false)
            Log.d(TAG, "서비스 중지됨")
        } else {
            val projectionManager = getSystemService(MediaProjectionManager::class.java)
            screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent())
        }
    }

    /**
     * 화면 캡처 동의 후 서비스 시작
     */
    private fun startServiceWithProjection(resultCode: Int, data: Intent) {
        // 추가: 캡처 권한 취득 직후 AI 모델 사전 빌드 및 로드를 백그라운드에서 진행 (콜드 스타트 지연 방지)
        com.s14p21a404.shutter.di.ServiceLocator.getInstance(this).detector.prepare()
        
        val intent = Intent(this, VideoCallDetectService::class.java).apply {
            putExtra(VideoCallDetectService.EXTRA_RESULT_CODE, resultCode)
            putExtra(VideoCallDetectService.EXTRA_PROJECTION_DATA, data)
        }
        startForegroundService(intent)
        viewModel.setServiceRunning(true)
        Log.d(TAG, "서비스 시작됨 (MediaProjection 포함)")
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}