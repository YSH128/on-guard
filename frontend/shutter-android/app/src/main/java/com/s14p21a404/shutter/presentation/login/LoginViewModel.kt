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
import com.s14p21a404.shutter.di.ServiceLocator
import com.s14p21a404.shutter.domain.usecase.GetAccessTokenUseCase
import com.s14p21a404.shutter.domain.usecase.LoginUseCase
import com.s14p21a404.shutter.domain.usecase.LogoutUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 로그인 상태:
 *  null  = 확인 중 (앱 시작 직후)
 *  false = 로그인 필요
 *  true  = 로그인 완료
 */
class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val serviceLocator = ServiceLocator.getInstance(application)
    private val loginUseCase: LoginUseCase = serviceLocator.loginUseCase
    private val logoutUseCase: LogoutUseCase = serviceLocator.logoutUseCase
    private val getAccessTokenUseCase: GetAccessTokenUseCase = serviceLocator.getAccessTokenUseCase

    private val _isLoggedIn = MutableStateFlow<Boolean?>(null)
    val isLoggedIn: StateFlow<Boolean?> = _isLoggedIn.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    init {
        checkLoginState()
    }

    /**
     * 로컬에 저장된 JWT Access Token이 있으면 바로 로그인 상태로 진입한다.
     */
    private fun checkLoginState() {
        _isLoggedIn.value = getAccessTokenUseCase()
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
                _isLoggedIn.value = false
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
     * 카카오 user ID를 먼저 조회한 뒤, 서버로 카카오 토큰 + user ID를 전달해 JWT를 발급받는다.
     */
    private fun loginToServer(kakaoAccessToken: String, kakaoRefreshToken: String) {
        UserApiClient.instance.me { user, error ->
            if (error != null || user?.id == null) {
                Log.e(TAG, "카카오 user ID 조회 실패", error)
                _loginError.value = "카카오 사용자 정보를 가져올 수 없습니다."
                _isLoggedIn.value = false
                return@me
            }
            val kakaoUserId = user.id!!
            viewModelScope.launch {
                loginUseCase(kakaoAccessToken, kakaoRefreshToken, kakaoUserId)
                    .onSuccess {
                        Log.d(TAG, "서버 JWT 발급 완료 (kakaoUserId=$kakaoUserId)")
                        _isLoggedIn.value = true
                    }
                    .onFailure { e ->
                        Log.e(TAG, "서버 로그인 실패", e)
                        _loginError.value = e.message ?: "서버 연결에 실패했습니다."
                        _isLoggedIn.value = false
                    }
            }
        }
    }

    /**
     * 에러 팝업 닫기
     */
    fun dismissLoginError() {
        _loginError.value = null
    }

    /**
     * 로그아웃: 저장된 토큰을 삭제하고 로그인 화면으로 이동한다.
     */
    fun logout() {
        logoutUseCase()
        _isLoggedIn.value = false
    }

    companion object {
        private const val TAG = "LoginViewModel"
    }
}