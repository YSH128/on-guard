package com.s14p21a404.shutter.domain.repository

import com.s14p21a404.shutter.domain.model.KakaoLoginResult

interface AuthRepository {
    suspend fun loginWithKakao(kakaoAccessToken: String, kakaoRefreshToken: String): Result<KakaoLoginResult>
    suspend fun refreshToken(): Result<Unit>
    fun hasToken(): Boolean
    fun getAccessToken(): String?
    fun getRefreshToken(): String?
    fun saveUserId(userId: Long)
    fun getUserId(): Long
    fun logout()
}
