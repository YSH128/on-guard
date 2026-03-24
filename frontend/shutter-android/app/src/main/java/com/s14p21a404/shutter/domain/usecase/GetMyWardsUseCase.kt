package com.s14p21a404.shutter.domain.usecase

import com.s14p21a404.shutter.domain.model.Ward
import com.s14p21a404.shutter.domain.repository.GuardianRepository

class GetMyWardsUseCase(private val repository: GuardianRepository) {
    suspend operator fun invoke(): Result<List<Ward>> = repository.getMyWards()
}