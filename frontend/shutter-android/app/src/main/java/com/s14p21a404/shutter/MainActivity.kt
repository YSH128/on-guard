package com.s14p21a404.shutter

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.s14p21a404.shutter.service.VideoCallDetectService
import com.s14p21a404.shutter.ui.theme.ShutterandroidTheme

class MainActivity : ComponentActivity() {

    private val serviceRunning = mutableStateOf(false)
    private val allPermissionsGranted = mutableStateOf(false)
    private val overlayPermissionGranted = mutableStateOf(false)

    // 런타임 권한 요청 런처
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        Log.d(TAG, "권한 결과: $permissions")
        checkAllPermissions()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        checkAllPermissions()

        setContent {
            ShutterandroidTheme {
                MainScreen(
                    isServiceRunning = serviceRunning.value,
                    allPermissionsGranted = allPermissionsGranted.value,
                    overlayPermissionGranted = overlayPermissionGranted.value,
                    onRequestPermissions = { requestAllPermissions() },
                    onRequestOverlayPermission = { requestOverlayPermission() },
                    onToggleService = { toggleService() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkAllPermissions()
    }

    /**
     * 모든 필요 권한 확인
     */
    private fun checkAllPermissions() {
        val hasPhoneState = ContextCompat.checkSelfPermission(
            this, Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        val hasNotification = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        val hasOverlay = Settings.canDrawOverlays(this)

        allPermissionsGranted.value = hasPhoneState && hasNotification
        overlayPermissionGranted.value = hasOverlay
        Log.d(TAG, "권한 상태 - Phone: $hasPhoneState, Notification: $hasNotification, Overlay: $hasOverlay")
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
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    /**
     * 서비스 시작/중지 토글
     */
    private fun toggleService() {
        if (serviceRunning.value) {
            stopService(Intent(this, VideoCallDetectService::class.java))
            serviceRunning.value = false
            Log.d(TAG, "서비스 중지됨")
        } else {
            val intent = Intent(this, VideoCallDetectService::class.java)
            startForegroundService(intent)
            serviceRunning.value = true
            Log.d(TAG, "서비스 시작됨")
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}

// ─────────────────────────────────────────────
// Compose UI
// ─────────────────────────────────────────────

@Composable
fun MainScreen(
    isServiceRunning: Boolean,
    allPermissionsGranted: Boolean,
    overlayPermissionGranted: Boolean,
    onRequestPermissions: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onToggleService: () -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 앱 제목
            Text(
                text = "🛡️ Shutter",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "실시간 영상통화 딥페이크 탐지",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(48.dp))

            // 상태 카드
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isServiceRunning)
                        Color(0xFFE8F5E9)
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isServiceRunning) "🟢 보호 활성화" else "⚪ 보호 비활성화",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isServiceRunning)
                            "영상통화를 실시간으로 모니터링 중입니다."
                        else
                            "서비스를 시작하여 보호를 활성화하세요.",
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 권한 미충족 시 권한 요청 버튼
            if (!allPermissionsGranted) {
                Button(
                    onClick = onRequestPermissions,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF9800)
                    )
                ) {
                    Text(
                        text = "권한 허용하기",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "서비스 실행에 필요한 권한을 허용해주세요.\n(전화 상태, 알림)",
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 오버레이 권한 미충족 시 설정 이동 버튼
            if (allPermissionsGranted && !overlayPermissionGranted) {
                Button(
                    onClick = onRequestOverlayPermission,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF5722)
                    )
                ) {
                    Text(
                        text = "오버레이 권한 허용하기",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "영상통화 위에 경고를 표시하려면\n'다른 앱 위에 표시' 권한이 필요합니다.\n설정 화면에서 직접 허용해주세요.",
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 모든 권한 충족 시 서비스 토글 버튼
            if (allPermissionsGranted && overlayPermissionGranted) {
                Button(
                    onClick = onToggleService,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isServiceRunning)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = if (isServiceRunning) "보호 중지" else "보호 시작",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}