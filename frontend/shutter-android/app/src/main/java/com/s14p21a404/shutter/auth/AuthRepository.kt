package com.s14p21a404.shutter.auth

import com.s14p21a404.shutter.network.ApiService
import com.s14p21a404.shutter.network.KakaoLoginRequest
import com.s14p21a404.shutter.network.RefreshRequest

class AuthRepository(
    private val apiService: ApiService,
    private val tokenManager: TokenManager,
) {

    /**
     * 카카오 토큰으로 서버 JWT를 발급받아 저장한다.
     * @return 성공 여부
     */
    suspend fun loginWithKakao(
        kakaoAccessToken: String,
        kakaoRefreshToken: String,
    ): Result<Unit> = runCatching {
        val response = apiService.loginWithKakao(
            KakaoLoginRequest(kakaoAccessToken, kakaoRefreshToken)
        )
        check(response.success) { response.message }
        tokenManager.saveTokens(response.data.accessToken, response.data.refreshToken)
    }

    /**
     * Refresh Token으로 새 Access Token을 발급받아 저장한다.
     * Refresh Token이 없거나 서버가 INVALID_REFRESH_TOKEN을 반환하면 토큰을 삭제한다.
     * @return 성공 여부
     */
    suspend fun refreshToken(): Result<Unit> = runCatching {
        val refreshToken = tokenManager.getRefreshToken()
            ?: error("INVALID_REFRESH_TOKEN")

        val response = apiService.refreshToken(RefreshRequest(refreshToken))
        check(response.success) { response.message }
        tokenManager.saveAccessToken(response.data)
    }.onFailure { e ->
        if (e.message?.contains("INVALID_REFRESH_TOKEN") == true) {
            tokenManager.clearTokens()
        }
    }
}