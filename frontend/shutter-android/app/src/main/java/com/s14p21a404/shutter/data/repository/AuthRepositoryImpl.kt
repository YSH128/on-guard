package com.s14p21a404.shutter.data.repository

import com.s14p21a404.shutter.data.token.TokenManager
import com.s14p21a404.shutter.domain.model.KakaoLoginResult
import com.s14p21a404.shutter.domain.repository.AuthRepository
import com.s14p21a404.shutter.network.ApiService
import com.s14p21a404.shutter.network.KakaoLoginRequest
import com.s14p21a404.shutter.network.RefreshRequest
import com.s14p21a404.shutter.network.RegisterRequest

class AuthRepositoryImpl(
    private val apiService: ApiService,
    private val tokenManager: TokenManager,
) : AuthRepository {

    override suspend fun loginWithKakao(
        kakaoAccessToken: String,
        kakaoRefreshToken: String,
    ): Result<KakaoLoginResult> = runCatching {
        val response = apiService.loginWithKakao(
            KakaoLoginRequest(
                kakaoAccessToken = kakaoAccessToken,
                kakaoRefreshToken = kakaoRefreshToken,
                deviceUuid = tokenManager.getOrCreateDeviceUuid(),
            )
        )
        check(response.success) { response.message }

        val data = response.data
        if (data.isNewUser) {
            val kakaoId = checkNotNull(data.kakaoId) { "신규 회원 응답에 kakaoId 없음" }
            KakaoLoginResult.NewUser(kakaoId)
        } else {
            val userId = checkNotNull(data.userId) { "기존 회원 응답에 userId 없음" }
            val accessToken = checkNotNull(data.accessToken) { "기존 회원 응답에 accessToken 없음" }
            val refreshToken = checkNotNull(data.refreshToken) { "기존 회원 응답에 refreshToken 없음" }
            tokenManager.saveTokens(accessToken, refreshToken)
            tokenManager.saveUserId(userId)
            KakaoLoginResult.LoggedIn(userId)
        }
    }

    override suspend fun register(kakaoId: Long, name: String): Result<Unit> = runCatching {
        val response = apiService.register(
            RegisterRequest(
                kakaoId = kakaoId,
                name = name,
                deviceUuid = tokenManager.getOrCreateDeviceUuid(),
            )
        )
        check(response.success) { response.message }
        tokenManager.saveTokens(response.data.accessToken, response.data.refreshToken)
        tokenManager.saveUserId(response.data.userId)
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

    override fun saveUserId(userId: Long) = tokenManager.saveUserId(userId)

    override fun getUserId(): Long = tokenManager.getUserId()

    override fun logout() = tokenManager.clearTokens()
}
