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
 * 보호자 등록 화면
 *
 * 참고 프로젝트 구성:
 *  - 상단 "보호자 등록" 타이틀 (뒤로가기 → 설정)
 *  - 검색 입력창 (보호자명 검색)
 *  - 보호자 목록 (체크 선택 가능)
 *  - 하단 "등록하기" 버튼
 */
@Composable
fun GuardianRegistrationScreen(
    onBack: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "보호자 등록 페이지",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "보호자 검색 + 선택 + 등록하기",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

