package com.s14p21a404.shutter.domain.model

data class Friend(
    val id: Long,
    val name: String,
    val profileImageUrl: String?,

    val kakaoId: Long
)