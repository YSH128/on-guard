package com.s14p21a404.shutter.presentation.login

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

@Composable
fun LoginScreen(
    onKakaoLogin: () -> Unit,
    loginError: String? = null,
    onDismissError: () -> Unit = {}
) {
    if (loginError != null) {
        AlertDialog(
            onDismissRequest = onDismissError,
            title = { Text(text = "로그인 실패") },
            text = { Text(text = loginError) },
            confirmButton = {
                TextButton(onClick = onDismissError) {
                    Text("확인")
                }
            }
        )
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(64.dp))

            // 카카오 로그인 버튼 (디자인은 추후 디자이너 작업 예정)
            Button(
                onClick = onKakaoLogin,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFEE500) // 카카오 공식 노란색
                )
            ) {
                Text(
                    text = "카카오로 로그인하기",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF191919) // 카카오 공식 텍스트 색상
                )
            }
        }
    }
}