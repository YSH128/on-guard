package com.s14p21a404.shutter.domain.model

data class GpsLocation(
    val wardId: Long,
    val name: String,
    val lat: Double?,
    val lng: Double?,
    val updatedAt: String?,
    val isSharing: Boolean,
)