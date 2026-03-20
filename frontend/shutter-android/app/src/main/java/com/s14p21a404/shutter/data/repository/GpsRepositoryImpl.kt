package com.s14p21a404.shutter.data.repository

import com.s14p21a404.shutter.domain.model.GpsLocation
import com.s14p21a404.shutter.domain.repository.GpsRepository
import com.s14p21a404.shutter.network.ApiService
import com.s14p21a404.shutter.network.GpsRequest
import com.s14p21a404.shutter.network.GpsSharingRequest

class GpsRepositoryImpl(private val apiService: ApiService) : GpsRepository {

    override suspend fun postGps(lat: Double, lng: Double): Result<Unit> = runCatching {
        val response = apiService.postGps(GpsRequest(lat, lng))
        check(response.success) { response.message }
    }

    override suspend fun getWardGps(wardId: Long): Result<GpsLocation> = runCatching {
        val response = apiService.getWardGps(wardId)
        check(response.success) { response.message }
        GpsLocation(
            wardId = response.data.wardId,
            name = response.data.name,
            lat = response.data.lat,
            lng = response.data.lng,
            updatedAt = response.data.updatedAt,
            isSharing = response.data.isSharing,
        )
    }

    override suspend fun toggleGpsSharing(enabled: Boolean): Result<Boolean> = runCatching {
        val response = apiService.toggleGpsSharing(GpsSharingRequest(enabled))
        check(response.success) { response.message }
        response.data.gpsSharing
    }
}
