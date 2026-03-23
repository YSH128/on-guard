package com.s14p21a404.shutter.presentation.login

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.common.model.ClientError
import com.kakao.sdk.common.model.ClientErrorCause
import com.kakao.sdk.user.UserApiClient
import com.google.firebase.messaging.FirebaseMessaging
import com.s14p21a404.shutter.di.ServiceLocator
import com.s14p21a404.shutter.domain.model.KakaoLoginResult
import com.s14p21a404.shutter.domain.usecase.GetAccessTokenUseCase
import com.s14p21a404.shutter.domain.usecase.LoginUseCase
import com.s14p21a404.shutter.push.ShutterFirebaseMessagingService
import com.s14p21a404.shutter.domain.usecase.LogoutUseCase
import com.s14p21a404.shutter.domain.usecase.RegisterUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class LoginUiState {
    /** 앱 시작 직후 저장된 토큰 확인 중 */
    object Checking : LoginUiState()
    /** 로그인 필요 */
    object LoggedOut : LoginUiState()
    /** 신규 회원 — 이름 입력 후 /auth/register 호출 필요 */
    data class NeedsRegistration(val kakaoId: Long) : LoginUiState()
    /** 로그인 완료 */
    object LoggedIn : LoginUiState()
}

class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val serviceLocator = ServiceLocator.getInstance(application)
    private val loginUseCase: LoginUseCase = serviceLocator.loginUseCase
    private val registerUseCase: RegisterUseCase = serviceLocator.registerUseCase
    private val logoutUseCase: LogoutUseCase = serviceLocator.logoutUseCase
    private val getAccessTokenUseCase: GetAccessTokenUseCase = serviceLocator.getAccessTokenUseCase

    private val _loginUiState = MutableStateFlow<LoginUiState>(LoginUiState.Checking)
    val loginUiState: StateFlow<LoginUiState> = _loginUiState.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    init {
        checkLoginState()
    }

    private fun checkLoginState() {
        _loginUiState.value = if (getAccessTokenUseCase()) {
            LoginUiState.LoggedIn
        } else {
            LoginUiState.LoggedOut
        }
    }

    /**
     * 카카오 로그인 시도.
     * 카카오톡 앱이 설치되어 있으면 카카오톡 로그인을 먼저 시도하고,
     * 실패하거나 미설치인 경우 카카오계정(웹뷰) 로그인으로 폴백한다.
     */
    fun loginWithKakao(context: Context) {
        val kakaoAccountCallback: (OAuthToken?, Throwable?) -> Unit = { token, error ->
            if (error != null) {
                Log.e(TAG, "카카오계정 로그인 실패", error)
                _loginUiState.value = LoginUiState.LoggedOut
            } else if (token != null) {
                Log.d(TAG, "카카오계정 로그인 성공 — 서버 인증 시작")
                loginToServer(token.accessToken, token.refreshToken ?: "")
            }
        }

        if (UserApiClient.instance.isKakaoTalkLoginAvailable(context)) {
            UserApiClient.instance.loginWithKakaoTalk(context) { token, error ->
                if (error != null) {
                    if (error is ClientError && error.reason == ClientErrorCause.Cancelled) {
                        Log.d(TAG, "카카오톡 로그인 사용자 취소")
                        return@loginWithKakaoTalk
                    }
                    Log.w(TAG, "카카오톡 로그인 실패, 카카오계정으로 폴백", error)
                    UserApiClient.instance.loginWithKakaoAccount(context, callback = kakaoAccountCallback)
                } else if (token != null) {
                    Log.d(TAG, "카카오톡 로그인 성공 — 서버 인증 시작")
                    loginToServer(token.accessToken, token.refreshToken ?: "")
                }
            }
        } else {
            UserApiClient.instance.loginWithKakaoAccount(context, callback = kakaoAccountCallback)
        }
    }

    /**
     * 카카오 토큰을 서버로 전달해 결과를 처리한다.
     *  - 기존 회원: JWT 저장 → LoggedIn
     *  - 신규 회원: kakaoId 보관 → NeedsRegistration
     */
    private fun loginToServer(kakaoAccessToken: String, kakaoRefreshToken: String) {
        viewModelScope.launch {
            loginUseCase(kakaoAccessToken, kakaoRefreshToken)
                .onSuccess { result ->
                    when (result) {
                        is KakaoLoginResult.LoggedIn -> {
                            Log.d(TAG, "로그인 완료 — userId: ${result.userId}")
                            registerFcmToken()
                            _loginUiState.value = LoginUiState.LoggedIn
                        }
                        is KakaoLoginResult.NewUser -> {
                            Log.d(TAG, "신규 회원 — kakaoId: ${result.kakaoId}")
                            _loginUiState.value = LoginUiState.NeedsRegistration(result.kakaoId)
                        }
                    }
                }
                .onFailure { e ->
                    Log.e(TAG, "서버 로그인 실패", e)
                    _loginError.value = e.message ?: "서버 연결에 실패했습니다."
                    _loginUiState.value = LoginUiState.LoggedOut
                }
        }
    }

    /**
     * 신규 회원 등록: 이름을 입력받아 /auth/register 호출 후 JWT 저장.
     */
    fun register(kakaoId: Long, name: String) {
        viewModelScope.launch {
            registerUseCase(kakaoId, name)
                .onSuccess {
                    Log.d(TAG, "회원가입 완료")
                    _loginUiState.value = LoginUiState.LoggedIn
                }
                .onFailure { e ->
                    Log.e(TAG, "회원가입 실패", e)
                    _loginError.value = e.message ?: "회원가입에 실패했습니다."
                }
        }
    }

    private fun registerFcmToken() {
        try {
            val deviceId = com.s14p21a404.shutter.data.token.TokenManager(getApplication())
                .getOrCreateDeviceUuid()
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token ->
                    Log.d(TAG, "FCM 토큰 획득 성공: $token")
                    ShutterFirebaseMessagingService.registerTokenWithKakao(token, deviceId)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "FCM 토큰 획득 실패", e)
                }
        } catch (e: Exception) {
            Log.e(TAG, "FirebaseMessaging 초기화 오류", e)
        }
    }

    fun dismissLoginError() {
        _loginError.value = null
    }

    fun logout() {
        logoutUseCase()
        _loginUiState.value = LoginUiState.LoggedOut
    }

    companion object {
        private const val TAG = "LoginViewModel"
    }
}
