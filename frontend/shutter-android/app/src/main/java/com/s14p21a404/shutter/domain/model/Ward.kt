package com.s14p21a404.shutter.domain.model

/**
 * 피보호자 도메인 모델 — GET /guardians/wards/me 응답 기반
 *
 * lat, lng, gpsUpdatedAt 모두 null이면 위치 미수신 상태 (GPS 꺼짐 또는 앱 미실행)
 */
data class Ward(
    val wardId: Long,
    val name: String,
    val lat: Double?,
    val lng: Double?,
    val gpsUpdatedAt: String?,
    val profileImageUrl: String? = null,
)