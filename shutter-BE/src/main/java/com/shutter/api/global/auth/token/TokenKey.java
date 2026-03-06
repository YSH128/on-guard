package com.shutter.api.global.auth.token;

/**
 * Redis 키 네이밍 전략
 *
 * 형식: {prefix}:{userId}
 *
 * 예시:
 *  kakao:access:{12345}   → 카카오 Access Token
 *  kakao:refresh:{12345}  → 카카오 Refresh Token
 *  jwt:refresh:{12345}    → 자체 JWT Refresh Token
 */
public final class TokenKey {

    private TokenKey() {}

    // 카카오 Access Token (메시지 전송에 사용) - TTL: 6시간
    public static String kakaoAccess(Long userId) {
        return "kakao:access:" + userId;
    }

    // 카카오 Refresh Token (Access Token 갱신에 사용) - TTL: 60일
    public static String kakaoRefresh(Long userId) {
        return "kakao:refresh:" + userId;
    }

    // 자체 JWT Refresh Token - TTL: 14일
    public static String jwtRefresh(Long userId) {
        return "jwt:refresh:" + userId;
    }
}
