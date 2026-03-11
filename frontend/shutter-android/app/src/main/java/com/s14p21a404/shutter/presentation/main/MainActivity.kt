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
import com.s14p21a404.shutter.presentation.service.VideoCallDetectService
import com.s14p21a404.shutter.ui.theme.ShutterandroidTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    // 런타임 권한 요청 런처
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        Log.d(TAG, "권한 결과: $permissions")
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
                val uiState by viewModel.uiState.collectAsState()
                MainScreen(
                    uiState = uiState,
                    onRequestPermissions = { requestAllPermissions() },
                    onRequestOverlayPermission = { requestOverlayPermission() },
                    onToggleService = { toggleService() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkAllPermissions()
    }

    /**
     * 필요한 모든 권한 요청
     */
    private fun requestAllPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.READ_PHONE_STATE)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
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

