package com.s14p21a404.shutter.domain.usecase

import com.s14p21a404.shutter.domain.model.Guardian
import com.s14p21a404.shutter.domain.repository.GuardianRepository

class AddGuardianUseCase(private val repository: GuardianRepository) {
    suspend operator fun invoke(guardianKakaoId: Long): Result<Guardian> =
        repository.registerGuardian(guardianKakaoId)
}