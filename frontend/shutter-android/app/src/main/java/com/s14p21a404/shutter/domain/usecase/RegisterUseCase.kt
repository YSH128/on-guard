package com.s14p21a404.shutter.domain.usecase

import com.s14p21a404.shutter.domain.repository.AuthRepository

class RegisterUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(kakaoId: Long, name: String): Result<Unit> =
        repository.register(kakaoId, name)
}
