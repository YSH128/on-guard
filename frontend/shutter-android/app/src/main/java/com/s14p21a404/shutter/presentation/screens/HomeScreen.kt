package com.s14p21a404.shutter.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 홈 화면
 *
 * 참고 프로젝트 구성:
 *  - 상단 그라데이션 헤더 (Shutter 로고 + 방패 아이콘 + "실시간 AI 감지 중" 상태 뱃지)
 *  - 알림 타임라인 (자동 발송, 위험 감지, 위치 공유 카드 목록)
 */
@Composable
fun HomeScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "홈 페이지",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "방패 아이콘 + 실시간 AI 감지 상태 + 알림 타임라인",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

