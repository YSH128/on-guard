package com.s14p21a404.shutter.data.repository

import com.s14p21a404.shutter.domain.model.Guardian
import com.s14p21a404.shutter.domain.model.Ward
import com.s14p21a404.shutter.domain.repository.GuardianRepository
import com.s14p21a404.shutter.network.ApiService
import com.s14p21a404.shutter.network.RegisterGuardianRequest

class GuardianRepositoryImpl(
    private val apiService: ApiService,
) : GuardianRepository {

    override suspend fun registerGuardian(guardianKakaoId: Long): Result<Guardian> = runCatching {
        val response = apiService.registerGuardian(RegisterGuardianRequest(guardianKakaoId))
        check(response.success) { response.message }
        Guardian(
            relationshipId = response.data.relationshipId,
            guardianId = response.data.guardianId,
            name = response.data.guardianName,
            createdAt = response.data.createdAt,
        )
    }

    override suspend fun getMyGuardians(): Result<List<Guardian>> = runCatching {
        val response = apiService.getMyGuardians()
        check(response.success) { response.message }
        response.data.guardians.map {
            Guardian(
                relationshipId = it.relationshipId,
                guardianId = it.guardianId,
                name = it.guardianName,
                createdAt = it.createdAt,
                profileImageUrl = it.profileImageUrl,
            )
        }
    }

    override suspend fun deleteGuardian(relationshipId: Long): Result<Unit> = runCatching {
        val response = apiService.deleteGuardian(relationshipId)
        check(response.success) { response.message }
    }

    override suspend fun getMyWards(): Result<List<Ward>> = runCatching {
        val response = apiService.getMyWards()
        check(response.success) { response.message }
        response.data.wards.map {
            Ward(
                wardId = it.wardId,
                name = it.name,
                lat = it.lat,
                lng = it.lng,
                gpsUpdatedAt = it.gpsUpdatedAt,
                profileImageUrl = it.profileImageUrl,
            )
        }
    }
}