package com.s14p21a404.shutter.domain.model

/**
 * 보호자 도메인 모델 — GET /guardians/me 응답 기반
 */
data class Guardian(
    val relationshipId: Long,
    val guardianId: Long,
    val name: String,
    val createdAt: String,
    val profileImageUrl: String? = null,
)