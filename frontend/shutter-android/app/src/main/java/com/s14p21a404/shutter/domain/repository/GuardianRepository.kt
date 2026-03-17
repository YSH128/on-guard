package com.s14p21a404.shutter.domain.repository

import com.s14p21a404.shutter.domain.model.Guardian
import com.s14p21a404.shutter.domain.model.Ward

interface GuardianRepository {
    /** POST /guardians — 피보호자가 보호자를 등록 */
    suspend fun registerGuardian(guardianKakaoId: Long): Result<Guardian>

    /** GET /guardians/me — 내 보호자 목록 조회 (피보호자 입장) */
    suspend fun getMyGuardians(): Result<List<Guardian>>

    /** DELETE /guardians/{relationshipId} — 보호자 관계 해제 */
    suspend fun deleteGuardian(relationshipId: Long): Result<Unit>

    /** GET /guardians/wards/me — 내 피보호자 목록 조회 (보호자 입장) */
    suspend fun getMyWards(): Result<List<Ward>>
}
