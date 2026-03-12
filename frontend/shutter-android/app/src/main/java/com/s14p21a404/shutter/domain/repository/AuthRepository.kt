package com.s14p21a404.shutter.domain.repository

interface AuthRepository {
    suspend fun loginWithKakao(kakaoAccessToken: String, kakaoRefreshToken: String): Result<Unit>
    suspend fun refreshToken(): Result<Unit>
    fun hasToken(): Boolean
    fun getAccessToken(): String?
    fun getRefreshToken(): String?
    fun logout()
}