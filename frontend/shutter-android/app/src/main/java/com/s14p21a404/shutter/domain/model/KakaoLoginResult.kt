package com.s14p21a404.shutter.domain.model

sealed class KakaoLoginResult {
    /** 로그인 완료 (신규/기존 모두) — JWT 발급 완료, 서버 userId 포함 */
    data class LoggedIn(val userId: Long) : KakaoLoginResult()
}