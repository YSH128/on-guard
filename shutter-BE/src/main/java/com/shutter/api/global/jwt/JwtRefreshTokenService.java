package com.shutter.api.global.jwt;

import com.shutter.api.global.auth.token.TokenKey;
import com.shutter.api.global.auth.token.TokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * JWT Refresh Token 관리 서비스
 *
 * Refresh Token은 Redis에 저장해 재사용 및 강제 만료(로그아웃)를 지원한다.
 * TTL은 JWT 설정의 refresh-expiry-ms와 동일하게 맞춘다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JwtRefreshTokenService {

    private final TokenRepository tokenRepository;

    @Value("${jwt.refresh-expiry-ms}")
    private long refreshExpiryMs;

    /** 로그인 시 Refresh Token Redis 저장 */
    public void save(Long userId, String refreshToken) {
        Duration ttl = Duration.ofMillis(refreshExpiryMs);
        tokenRepository.save(TokenKey.jwtRefresh(userId), refreshToken, ttl);
        log.debug("JWT refresh token saved. userId={}", userId);
    }

    /** Refresh Token 조회 */
    public Optional<String> find(Long userId) {
        return tokenRepository.find(TokenKey.jwtRefresh(userId));
    }

    /** 로그아웃 시 Refresh Token 삭제 */
    public void delete(Long userId) {
        tokenRepository.delete(TokenKey.jwtRefresh(userId));
        log.info("JWT refresh token deleted. userId={}", userId);
    }
}
