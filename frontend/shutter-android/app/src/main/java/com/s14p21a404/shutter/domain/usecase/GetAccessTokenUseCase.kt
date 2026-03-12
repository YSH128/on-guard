package com.s14p21a404.shutter.domain.usecase

import com.s14p21a404.shutter.domain.repository.AuthRepository

class GetAccessTokenUseCase(private val repository: AuthRepository) {
    operator fun invoke(): Boolean = repository.hasToken()
}