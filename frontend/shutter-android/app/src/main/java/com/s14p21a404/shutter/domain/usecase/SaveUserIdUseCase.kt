package com.s14p21a404.shutter.domain.usecase

import com.s14p21a404.shutter.domain.repository.AuthRepository

class SaveUserIdUseCase(private val repository: AuthRepository) {
    operator fun invoke(userId: Long) = repository.saveUserId(userId)
}