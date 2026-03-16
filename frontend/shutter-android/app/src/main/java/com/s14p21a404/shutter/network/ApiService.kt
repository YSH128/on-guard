package com.s14p21a404.shutter.network

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.POST

// ── 카카오 로그인 ──────────────────────────────────────────────────────────────

data class KakaoLoginRequest(
    @SerializedName("kakao_access_token") val kakaoAccessToken: String,
    @SerializedName("kakao_refresh_token") val kakaoRefreshToken: String,
    @SerializedName("device_uuid") val deviceUuid: String,
)

/**
 * /auth/kakao 응답의 data 필드.
 *
 * 기존 회원: isNewUser=false → userId, accessToken, refreshToken 존재
 * 신규 회원: isNewUser=true  → kakaoId 만 존재
 */
data class KakaoLoginData(
    @SerializedName("is_new_user") val isNewUser: Boolean,
    @SerializedName("user_id") val userId: Long? = null,
    @SerializedName("access_token") val accessToken: String? = null,
    @SerializedName("refresh_token") val refreshToken: String? = null,
    @SerializedName("kakao_id") val kakaoId: Long? = null,
)

data class KakaoLoginResponse(
    val success: Boolean,
    val message: String,
    val data: KakaoLoginData,
)

// ── 신규 회원 등록 ──────────────────────────────────────────────────────────────

data class RegisterRequest(
    @SerializedName("kakao_id") val kakaoId: Long,
    val name: String,
    @SerializedName("device_uuid") val deviceUuid: String,
)

data class RegisterData(
    @SerializedName("is_new_user") val isNewUser: Boolean,
    @SerializedName("user_id") val userId: Long,
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String,
)

data class RegisterResponse(
    val success: Boolean,
    val message: String,
    val data: RegisterData,
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

// ── 인터페이스 ─────────────────────────────────────────────────────────────────

interface ApiService {

    @POST("api/v1/auth/kakao")
    suspend fun loginWithKakao(@Body request: KakaoLoginRequest): KakaoLoginResponse

    @POST("api/v1/auth/register")
    suspend fun register(@Body request: RegisterRequest): RegisterResponse

    @POST("api/v1/auth/refresh")
    suspend fun refreshToken(@Body request: RefreshRequest): RefreshResponse
}
