package com.s14p21a404.shutter.domain.usecase

import com.s14p21a404.shutter.domain.repository.GuardianRepository

class DeleteGuardianUseCase(private val repository: GuardianRepository) {
    suspend operator fun invoke(relationshipId: Long): Result<Unit> =
        repository.deleteGuardian(relationshipId)
}