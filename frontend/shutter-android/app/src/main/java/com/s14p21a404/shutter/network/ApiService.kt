package com.s14p21a404.shutter.network

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.POST

// ── 카카오 로그인 ──────────────────────────────────────────────────────────────

data class KakaoLoginRequest(
    @SerializedName("kakao_access_token") val kakaoAccessToken: String,
    @SerializedName("kakao_refresh_token") val kakaoRefreshToken: String,
    @SerializedName("kakao_user_id") val kakaoUserId: Long,
)

data class KakaoLoginResponse(
    val success: Boolean,
    val message: String,
    val data: TokenData,
)

// ── 토큰 갱신 ──────────────────────────────────────────────────────────────────

data class RefreshRequest(
    @SerializedName("refresh_token") val refreshToken: String,
)

data class RefreshResponse(
    val success: Boolean,
    val message: String,
    val data: String, // 새로운 access_token
)

// ── 공통 DTO ───────────────────────────────────────────────────────────────────

data class TokenData(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String,
)

// ── 인터페이스 ─────────────────────────────────────────────────────────────────

interface ApiService {

    @POST("api/v1/auth/kakao")
    suspend fun loginWithKakao(@Body request: KakaoLoginRequest): KakaoLoginResponse

    @POST("api/v1/auth/refresh")
    suspend fun refreshToken(@Body request: RefreshRequest): RefreshResponse
}