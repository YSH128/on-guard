package com.s14p21a404.shutter.domain.usecase

import com.s14p21a404.shutter.domain.model.Guardian
import com.s14p21a404.shutter.domain.repository.GuardianRepository

class GetMyGuardiansUseCase(private val repository: GuardianRepository) {
    suspend operator fun invoke(): Result<List<Guardian>> = repository.getMyGuardians()
}