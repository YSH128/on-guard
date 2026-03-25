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
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.s14p21a404.shutter.presentation.login.LoginScreen
import com.s14p21a404.shutter.presentation.login.LoginUiState
import com.s14p21a404.shutter.presentation.login.LoginViewModel
import com.s14p21a404.shutter.presentation.service.VideoCallDetectService
import com.s14p21a404.shutter.push.IncomingCallEventBus
import com.s14p21a404.shutter.ui.theme.ShutterandroidTheme

class MainActivity : ComponentActivity() {

    private val loginViewModel: LoginViewModel by viewModels()
    private val viewModel: MainViewModel by viewModels()

    // 핵심: 통화 상태
    private var isInCall = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.checkAllPermissions()
    }

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            startServiceWithProjection(result.resultCode, result.data!!)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleDeepLinkIntent(intent)

        setContent {
            ShutterandroidTheme {

                LaunchedEffect(Unit) {
                    IncomingCallEventBus.callRejected.collect {
                        viewModel.onCallRejected()
                    }
                }

                val loginUiState by loginViewModel.loginUiState.collectAsState()
                val loginError by loginViewModel.loginError.collectAsState()

                when (loginUiState) {

                    LoginUiState.Checking -> {}

                    LoginUiState.LoggedOut -> {
                        LoginScreen(
                            onKakaoLogin = { loginViewModel.loginWithKakao(this@MainActivity) },
                            loginError = loginError,
                            onDismissError = { loginViewModel.dismissLoginError() }
                        )
                    }

                    LoginUiState.LoggedIn -> {

                        val uiState by viewModel.uiState.collectAsState()
                        val pendingScreen by viewModel.pendingDeepLinkScreen.collectAsState()

                        MainScreen(
                            uiState = uiState,
                            viewModel = viewModel,
                            pendingDeepLinkScreen = pendingScreen,
                            onDeepLinkHandled = { viewModel.setDeepLinkScreen(null) },
                            onRequestPermissions = { requestAllPermissions() },
                            onRequestOverlayPermission = { requestOverlayPermission() },
                            onRequestAccessibilityPermission = { requestAccessibilityPermission() },
                            onToggleService = { toggleService() },
                            onLogout = { loginViewModel.logout() },

                            // 영상통화 진입
                            onNavigateToVideoCall = { channelId, callerName, opponentKakaoId, token ->

                                Log.d(TAG, "영상통화 진입: $channelId")

                                isInCall = true

                                viewModel.setDeepLinkScreen(
                                    "video_call:$channelId:$callerName:$opponentKakaoId:$token"
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLinkIntent(intent)
    }

    private fun handleDeepLinkIntent(intent: Intent?) {

        val screenFromUri = intent?.data?.getQueryParameter("screen")
        val screenFromExtra = intent?.getStringExtra("screen")

        val screen = screenFromUri ?: screenFromExtra ?: return

        Log.d(TAG, "DeepLink screen: $screen")

        // 이미 통화중이면 중복 진입 방지
        if (screen.startsWith("video_call") && isInCall) {
            Log.d(TAG, "이미 통화중 → video_call 무시")
            return
        }

        when (screen) {

            "incoming_call" -> {
                val channelName = intent?.getStringExtra("channel_name") ?: return
                val callerName = intent?.getStringExtra("caller_name") ?: "알 수 없음"
                val callerKakaoId = intent?.getLongExtra("caller_kakao_id", 0L) ?: 0L

                Log.d(TAG, "incoming_call 처리: $channelName / $callerName / kakaoId=$callerKakaoId")

                viewModel.onIncomingCall(channelName, callerName, callerKakaoId)
                viewModel.setDeepLinkScreen("incoming_call")
            }

            "reject_call" -> {
                val channelName = intent?.getStringExtra("channel_name") ?: return
                Log.d(TAG, "reject_call 처리: $channelName")
            }

            "call_accepted" -> {

                val channelName = intent?.getStringExtra("channel_name") ?: return
                val token = intent?.getStringExtra("token") ?: return   // 🔥 추가

                Log.d(TAG, "CALL_ACCEPTED 처리: $channelName / token=$token")

                isInCall = true

                viewModel.setDeepLinkScreen(
                    "video_call:$channelName:상대방:$token"
                )
            }

            else -> {
                viewModel.setDeepLinkScreen(screen)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkAllPermissions()
    }

    private fun requestAccessibilityPermission() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    /**
     * 필요한 모든 권한 요청
     */
    private fun requestAllPermissions() {
        val permissions = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) permissions.add(Manifest.permission.READ_PHONE_STATE)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) permissions.add(Manifest.permission.POST_NOTIFICATIONS)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ANSWER_PHONE_CALLS)
            != PackageManager.PERMISSION_GRANTED
        ) permissions.add(Manifest.permission.ANSWER_PHONE_CALLS)

        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    /**
     * 오버레이 권한 요청 (설정 화면으로 이동)
     */
    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    "package:$packageName".toUri()
                )
            )
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
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}