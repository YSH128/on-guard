package com.s14p21a404.shutter.domain.usecase

import com.s14p21a404.shutter.domain.repository.AuthRepository

class LogoutUseCase(private val repository: AuthRepository) {
    operator fun invoke() = repository.logout()
}