package com.s14p21a404.shutter.network

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

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

// ── 보호자 등록 POST /guardians ────────────────────────────────────────────────

data class RegisterGuardianRequest(
    @SerializedName("guardian_kakao_id") val guardianKakaoId: Long,
)

data class RegisterGuardianData(
    @SerializedName("relationship_id") val relationshipId: Long,
    @SerializedName("guardian_id") val guardianId: Long,
    @SerializedName("guardian_name") val guardianName: String,
    @SerializedName("created_at") val createdAt: String,
)

data class RegisterGuardianResponse(
    val success: Boolean,
    val message: String,
    val data: RegisterGuardianData,
)

// ── 내 보호자 목록 GET /guardians/me ──────────────────────────────────────────

data class GuardianItem(
    @SerializedName("relationship_id") val relationshipId: Long,
    @SerializedName("guardian_id") val guardianId: Long,
    @SerializedName("guardian_name") val guardianName: String,
    @SerializedName("created_at") val createdAt: String,
)

data class GuardianListData(val guardians: List<GuardianItem>)

data class GuardianListResponse(
    val success: Boolean,
    val message: String,
    val data: GuardianListData,
)

// ── 보호자 관계 해제 DELETE /guardians/{relationshipId} ───────────────────────

data class DeleteGuardianData(
    @SerializedName("relationship_id") val relationshipId: Long,
)

data class DeleteGuardianResponse(
    val success: Boolean,
    val message: String,
    val data: DeleteGuardianData,
)

// ── 내 피보호자 목록 GET /guardians/wards/me ──────────────────────────────────

data class WardItem(
    @SerializedName("ward_id") val wardId: Long,
    val name: String,
    val lat: Double?,
    val lng: Double?,
    @SerializedName("gps_updated_at") val gpsUpdatedAt: String?,
)

data class WardListData(val wards: List<WardItem>)

data class WardListResponse(
    val success: Boolean,
    val message: String,
    val data: WardListData,
)

// ── 인터페이스 ─────────────────────────────────────────────────────────────────

interface ApiService {

    @POST("api/v1/auth/kakao")
    suspend fun loginWithKakao(@Body request: KakaoLoginRequest): KakaoLoginResponse

    @POST("api/v1/auth/register")
    suspend fun register(@Body request: RegisterRequest): RegisterResponse

    @POST("api/v1/auth/refresh")
    suspend fun refreshToken(@Body request: RefreshRequest): RefreshResponse

    @POST("api/v1/guardians")
    suspend fun registerGuardian(@Body request: RegisterGuardianRequest): RegisterGuardianResponse

    @GET("api/v1/guardians/me")
    suspend fun getMyGuardians(): GuardianListResponse

    @DELETE("api/v1/guardians/{relationshipId}")
    suspend fun deleteGuardian(@Path("relationshipId") relationshipId: Long): DeleteGuardianResponse

    @GET("api/v1/guardians/wards/me")
    suspend fun getMyWards(): WardListResponse
}