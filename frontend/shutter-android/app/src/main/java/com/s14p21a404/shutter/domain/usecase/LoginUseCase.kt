package com.s14p21a404.shutter.domain.usecase

import com.s14p21a404.shutter.domain.model.KakaoLoginResult
import com.s14p21a404.shutter.domain.repository.AuthRepository

class LoginUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(kakaoAccessToken: String, kakaoRefreshToken: String): Result<KakaoLoginResult> =
        repository.loginWithKakao(kakaoAccessToken, kakaoRefreshToken)
}
