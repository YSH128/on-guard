package com.s14p21a404.shutter.presentation.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.s14p21a404.shutter.presentation.navigation.AppScaffold

private val AccentColor = Color(0xFF6083EE)
private val ButtonColor = Color(0xFF2B3A5C)
private val LabelColor = Color(0xFF1A1A1A)
private val PlaceholderColor = Color(0xFF9CA3AF)
private val UnfocusedLineColor = Color(0xFFD1D5DB)

@Composable
fun MainScreen(
    uiState: MainUiState,
    isNewUser: Boolean = false,
    onRegister: (name: String) -> Unit = {},
    onRequestPermissions: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onRequestAccessibilityPermission: () -> Unit = {},
    onToggleService: () -> Unit,
    onLogout: () -> Unit,
    loginError: String? = null,
    onDismissError: () -> Unit = {},
) {
    if (isNewUser) {
        RegistrationScreen(
            onBack = onLogout,
            onRegister = onRegister,
            loginError = loginError,
            onDismissError = onDismissError,
        )
        return
    }

    AppScaffold(
        onLogout = onLogout,
        isServiceRunning = uiState.isServiceRunning,
        onToggleService = onToggleService,
        uiState = uiState,
        onRequestPermissions = onRequestPermissions,
        onRequestOverlayPermission = onRequestOverlayPermission,
        onRequestAccessibilityPermission = onRequestAccessibilityPermission,
    )
}

/**
 * 신규 회원 등록 화면 — 참고 디자인(b_mlBd8AWG4QY) 기반
 *
 * 구성:
 *  - 상단 뒤로가기 아이콘
 *  - "이름을 입력해주세요." 큰 타이틀
 *  - "이름" 라벨 + 밑줄 입력창 (포커스 시 AccentColor)
 *  - 하단 "시작하기" 버튼
 */
@Composable
private fun RegistrationScreen(
    onBack: () -> Unit,
    onRegister: (name: String) -> Unit,
    loginError: String?,
    onDismissError: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    // 화면 진입 시 자동 포커스 → 키보드 표시
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    if (loginError != null) {
        AlertDialog(
            onDismissRequest = onDismissError,
            confirmButton = {
                TextButton(onClick = onDismissError) { Text("확인") }
            },
            title = { Text("오류") },
            text = { Text(loginError) },
        )
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // ── 상단 뒤로가기 ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, top = 8.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Filled.ChevronLeft,
                        contentDescription = "뒤로가기",
                        modifier = Modifier.size(28.dp),
                        tint = AccentColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            // ── 이름 입력 영역 ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                // 라벨
                Text(
                    text = "이름",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = LabelColor,
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 밑줄 입력창
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = {
                        Text(
                            text = "이름을 입력해주세요",
                            fontSize = 20.sp,
                            color = PlaceholderColor,
                        )
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { isFocused = it.isFocused },
                    textStyle = TextStyle(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        color = LabelColor,
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = AccentColor,
                        unfocusedIndicatorColor = UnfocusedLineColor,
                        cursorColor = AccentColor,
                    ),
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // ── 하단 시작하기 버튼 ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 40.dp)
            ) {
                Button(
                    onClick = { if (name.isNotBlank()) onRegister(name.trim()) },
                    enabled = name.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ButtonColor,
                        contentColor = Color.White,
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
                ) {
                    Text(
                        text = "시작하기",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}