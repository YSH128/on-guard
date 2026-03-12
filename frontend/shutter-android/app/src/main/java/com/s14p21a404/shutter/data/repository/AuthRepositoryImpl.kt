package com.s14p21a404.shutter.data.repository

import com.s14p21a404.shutter.data.token.TokenManager
import com.s14p21a404.shutter.domain.repository.AuthRepository
import com.s14p21a404.shutter.network.ApiService
import com.s14p21a404.shutter.network.KakaoLoginRequest
import com.s14p21a404.shutter.network.RefreshRequest

class AuthRepositoryImpl(
    private val apiService: ApiService,
    private val tokenManager: TokenManager,
) : AuthRepository {

    override suspend fun loginWithKakao(
        kakaoAccessToken: String,
        kakaoRefreshToken: String,
    ): Result<Unit> = runCatching {
        val response = apiService.loginWithKakao(
            KakaoLoginRequest(kakaoAccessToken, kakaoRefreshToken)
        )
        check(response.success) { response.message }
        tokenManager.saveTokens(response.data.accessToken, response.data.refreshToken)
    }

    override suspend fun refreshToken(): Result<Unit> = runCatching {
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

    override fun hasToken(): Boolean = tokenManager.hasToken()

    override fun getAccessToken(): String? = tokenManager.getAccessToken()

    override fun getRefreshToken(): String? = tokenManager.getRefreshToken()

    override fun logout() = tokenManager.clearTokens()
}