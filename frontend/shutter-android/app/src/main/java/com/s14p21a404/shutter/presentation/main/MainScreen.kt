package com.s14p21a404.shutter.presentation.main

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
import com.s14p21a404.shutter.presentation.navigation.AppScaffold

@Suppress("UNUSED_PARAMETER")
@Composable
fun MainScreen(
    uiState: MainUiState,
    onRequestPermissions: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onToggleService: () -> Unit,
    onLogout: () -> Unit
) {
    // 모든 권한이 충족되면 → AppScaffold (하단 네비게이션 + 홈/위치/설정 페이지)
    if (uiState.allPermissionsGranted && uiState.overlayPermissionGranted) {
        AppScaffold(onLogout = onLogout)
    } else {
        // 권한 미충족 → 기존 권한 요청 UI
        PermissionRequestScreen(
            uiState = uiState,
            onRequestPermissions = onRequestPermissions,
            onRequestOverlayPermission = onRequestOverlayPermission,
            onLogout = onLogout
        )
    }
}

/**
 * 권한 요청 화면 — 필요한 권한을 안내하고 요청 버튼을 보여준다.
 */
@Composable
private fun PermissionRequestScreen(
    uiState: MainUiState,
    onRequestPermissions: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onLogout: () -> Unit,
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

            // 권한 미충족 시 권한 요청 버튼
            if (!uiState.allPermissionsGranted) {
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
            if (uiState.allPermissionsGranted && !uiState.overlayPermissionGranted) {
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


            Spacer(modifier = Modifier.height(24.dp))

            // 로그아웃 버튼
            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Text(
                    text = "로그아웃",
                    fontSize = 15.sp
                )
            }
        }
    }
}

