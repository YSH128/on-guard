package com.shutter.api.global.auth.token;

import java.time.Duration;
import java.util.Optional;

/**
 * Redis 기반 토큰 저장소 인터페이스
 *
 * 저장 대상:
 *  - Kakao Access Token  : 메시지 전송(알림) 시 사용
 *  - Kakao Refresh Token : Access Token 갱신 시 사용
 *  - JWT Refresh Token   : 자체 API 인증 갱신 시 사용
 */
public interface TokenRepository {

    /** 토큰 저장 (TTL 포함) */
    void save(String key, String value, Duration ttl);

    /** 토큰 조회 */
    Optional<String> find(String key);

    /** 토큰 삭제 (로그아웃 / 만료 처리) */
    void delete(String key);

    /** 토큰 존재 여부 확인 */
    boolean exists(String key);
}
