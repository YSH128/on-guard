package com.s14p21a404.shutter.domain.repository

import com.s14p21a404.shutter.domain.model.GpsLocation

interface GpsRepository {
    /** POST /api/v1/gps — 피보호자가 현재 위치 전송 */
    suspend fun postGps(lat: Double, lng: Double): Result<Unit>

    /** GET /api/v1/gps/{wardId} — 보호자가 피보호자 위치 조회 */
    suspend fun getWardGps(wardId: Long): Result<GpsLocation>

    /** PUT /api/v1/gps/sharing — GPS 공유 토글 */
    suspend fun toggleGpsSharing(enabled: Boolean): Result<Boolean>
}