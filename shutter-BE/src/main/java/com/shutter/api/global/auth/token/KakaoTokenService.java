package com.shutter.api.global.auth.token;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * 카카오 OAuth 토큰 관리 서비스
 *
 * 카카오 로그인 후 발급된 Access/Refresh Token을 Redis에 저장해두고
 * 가디언 알림 메시지 전송 시 Access Token을 꺼내 사용한다.
 *
 * TTL 기준 (카카오 공식 문서 기준):
 *  - Access Token  : 12시간
 *  - Refresh Token : 60일 (30일 미만 남으면 갱신 가능)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KakaoTokenService {

    private static final Duration ACCESS_TOKEN_TTL  = Duration.ofHours(12);
    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(60);

    private final TokenRepository tokenRepository;

    /** 카카오 로그인 성공 시 Access + Refresh Token 저장 */
    public void saveTokens(Long userId, String accessToken, String refreshToken) {
        tokenRepository.save(TokenKey.kakaoAccess(userId), accessToken, ACCESS_TOKEN_TTL);
        tokenRepository.save(TokenKey.kakaoRefresh(userId), refreshToken, REFRESH_TOKEN_TTL);
        log.info("Kakao tokens saved for userId={}", userId);
    }

    /** 가디언 메시지 전송 시 Access Token 조회 */
    public Optional<String> getAccessToken(Long userId) {
        return tokenRepository.find(TokenKey.kakaoAccess(userId));
    }

    /** Access Token 갱신 후 저장 */
    public void updateAccessToken(Long userId, String newAccessToken) {
        tokenRepository.save(TokenKey.kakaoAccess(userId), newAccessToken, ACCESS_TOKEN_TTL);
        log.info("Kakao access token refreshed for userId={}", userId);
    }

    /** Refresh Token 조회 (Access Token 만료 시 갱신에 사용) */
    public Optional<String> getRefreshToken(Long userId) {
        return tokenRepository.find(TokenKey.kakaoRefresh(userId));
    }

    /** 로그아웃 시 토큰 모두 삭제 */
    public void deleteTokens(Long userId) {
        tokenRepository.delete(TokenKey.kakaoAccess(userId));
        tokenRepository.delete(TokenKey.kakaoRefresh(userId));
        log.info("Kakao tokens deleted for userId={}", userId);
    }
}
