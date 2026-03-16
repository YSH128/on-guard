package com.s14p21a404.shutter.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 환경 설정 화면
 *
 * 참고 프로젝트 구성:
 *  - 상단 "환경 설정" 타이틀 (뒤로가기 → 홈)
 *  - 알림 설정 섹션: GPS 위치 공유 토글
 *  - 보호자 목록 섹션: 등록된 보호자 카드 리스트 + 삭제 버튼
 *  - 보호자 추가 버튼 → GuardianRegistrationScreen으로 이동
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun SettingsScreen(
    onNavigateToGuardianRegistration: () -> Unit,
    onLogout: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = "환경 설정 페이지",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "GPS 위치 공유 설정 + 보호자 목록 관리",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

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

