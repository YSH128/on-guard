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
import com.s14p21a404.shutter.auth.AuthRepository
import com.s14p21a404.shutter.auth.TokenManager
import com.s14p21a404.shutter.network.RetrofitClient
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

    private val tokenManager = TokenManager(application)
    private val authRepository = AuthRepository(
        apiService = RetrofitClient.create(tokenManager),
        tokenManager = tokenManager,
    )

    private val _isLoggedIn = MutableStateFlow<Boolean?>(null)
    val isLoggedIn: StateFlow<Boolean?> = _isLoggedIn.asStateFlow()

    init {
        checkLoginState()
    }

    /**
     * 로컬에 저장된 JWT Access Token이 있으면 바로 로그인 상태로 진입한다.
     */
    private fun checkLoginState() {
        _isLoggedIn.value = tokenManager.hasToken()
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
     * 카카오 토큰을 서버로 전달해 JWT를 발급받고 저장한다.
     */
    private fun loginToServer(kakaoAccessToken: String, kakaoRefreshToken: String) {
        viewModelScope.launch {
            authRepository.loginWithKakao(kakaoAccessToken, kakaoRefreshToken)
                .onSuccess {
                    Log.d(TAG, "서버 JWT 발급 완료")
                    _isLoggedIn.value = true
                }
                .onFailure { e ->
                    Log.e(TAG, "서버 로그인 실패", e)
                    _isLoggedIn.value = false
                }
        }
    }

    /**
     * 로그아웃: 저장된 토큰을 삭제하고 로그인 화면으로 이동한다.
     */
    fun logout() {
        tokenManager.clearTokens()
        _isLoggedIn.value = false
    }

    companion object {
        private const val TAG = "LoginViewModel"
    }
}