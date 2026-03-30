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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.s14p21a404.shutter.presentation.navigation.AppScaffold

private val AccentColor = Color(0xFF6083EE)
private val ButtonColor = Color(0xFF2B3A5C)

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
    pendingDeepLinkScreen: String? = null,
    onDeepLinkHandled: () -> Unit = {},
) {

    // 기존 유지
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
        pendingDeepLinkScreen = pendingDeepLinkScreen,
        onDeepLinkHandled = onDeepLinkHandled,
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
fun RegistrationScreen(
    onBack: () -> Unit,
    onRegister: (name: String) -> Unit,
    loginError: String?,
    onDismissError: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(modifier = Modifier.fillMaxSize()) {

        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.Filled.ChevronLeft,
                contentDescription = null
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        TextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.focusRequester(focusRequester)
        )

        Spacer(modifier = Modifier.weight(1f))

        Button(onClick = { onRegister(name) }) {
            Text("시작")
        }
    }
}