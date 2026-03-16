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
 * 위치 확인 화면
 *
 * 참고 프로젝트 구성:
 *  - 상단 "위치 확인" 타이틀
 *  - 보호자 선택 가로 스크롤 (아바타 + 이름)
 *  - 지도 영역 (선택된 보호자의 실시간 위치 표시, 방패 마커)
 *  - 보호자 미등록 시 빈 상태 안내
 */
@Composable
fun LocationScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "위치 확인 페이지",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "보호자 선택 + 지도에서 위치 확인",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

