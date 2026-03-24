package com.s14p21a404.shutter.presentation.criminal

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.s14p21a404.shutter.presentation.login.LoginScreen
import com.s14p21a404.shutter.presentation.login.LoginUiState
import com.s14p21a404.shutter.presentation.login.LoginViewModel
import com.s14p21a404.shutter.presentation.service.VideoCallDetectService
import com.s14p21a404.shutter.ui.theme.ShutterandroidTheme

/**
 * 범죄자 시연 앱 진입점.
 *
 * 카카오 로그인 후 세 가지 화면을 제공한다:
 *   criminal_home  — 타겟 목록 + 얼굴 설정 진입점
 *   face_setup     — 타겟 얼굴 사진 설정
 *   criminal_video/{channelId} — 딥페이크 영상 송출 영상통화
 */
class CriminalActivity : ComponentActivity() {

    private val loginViewModel: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 범죄자 앱에서는 딥페이크 탐지 서비스가 필요 없으므로, 혹시 켜져 있으면 끈다.
        stopService(Intent(this, VideoCallDetectService::class.java))

        setContent {
            ShutterandroidTheme {
                val loginUiState by loginViewModel.loginUiState.collectAsState()
                val loginError  by loginViewModel.loginError.collectAsState()

                when (loginUiState) {
                    LoginUiState.Checking -> Unit // 스플래시 대체

                    LoginUiState.LoggedOut -> {
                        LoginScreen(
                            onKakaoLogin = { loginViewModel.loginWithKakao(this@CriminalActivity) },
                            loginError = loginError,
                            onDismissError = { loginViewModel.dismissLoginError() },
                        )
                    }

                    LoginUiState.LoggedIn -> {
                        val navController = rememberNavController()

                        // FaceSetupViewModel을 최상위에서 생성해 화면 간 공유
                        val faceSetupViewModel: FaceSetupViewModel = viewModel()

                        NavHost(navController = navController, startDestination = "criminal_home") {

                            composable("criminal_home") {
                                CriminalHomeScreen(
                                    onNavigateToFaceSetup = {
                                        navController.navigate("face_setup")
                                    },
                                    onNavigateToVideoCall = { channelId ->
                                        navController.navigate("criminal_video/$channelId")
                                    },
                                    faceSetupViewModel = faceSetupViewModel,
                                    onLogout = { loginViewModel.logout() },
                                )
                            }

                            composable("face_setup") {
                                FaceSetupScreen(
                                    onBack = { navController.popBackStack() },
                                    viewModel = faceSetupViewModel,
                                )
                            }

                            composable("criminal_video/{channelId}") { backStack ->
                                val channelId = backStack.arguments?.getString("channelId") ?: ""
                                CriminalVideoCallScreen(
                                    channelId = channelId,
                                    faceSetupViewModel = faceSetupViewModel,
                                    onCallEnd = { navController.popBackStack() },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}