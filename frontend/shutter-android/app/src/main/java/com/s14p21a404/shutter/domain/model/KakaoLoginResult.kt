package com.s14p21a404.shutter.domain.model

sealed class KakaoLoginResult {
    /** 기존 회원 — JWT 발급 완료, 서버 userId 포함 */
    data class LoggedIn(val userId: Long) : KakaoLoginResult()

    /** 신규 회원 — 회원가입 필요, kakaoId 임시 보관 */
    data class NewUser(val kakaoId: Long) : KakaoLoginResult()
}
