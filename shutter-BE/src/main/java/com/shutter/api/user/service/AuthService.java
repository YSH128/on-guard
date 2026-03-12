package com.shutter.api.user.service;

import com.shutter.api.global.auth.kakao.KakaoApiClient;
import com.shutter.api.global.auth.kakao.KakaoUserInfoResponse;
import com.shutter.api.global.auth.token.KakaoTokenService;
import com.shutter.api.global.exception.BusinessException;
import com.shutter.api.global.exception.ErrorCode;
import com.shutter.api.global.jwt.JwtProvider;
import com.shutter.api.global.jwt.JwtRefreshTokenService;
import com.shutter.api.user.domain.User;
import com.shutter.api.user.dto.request.KakaoLoginRequest;
import com.shutter.api.user.dto.response.LoginResponse;
import com.shutter.api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final KakaoApiClient kakaoApiClient;
    private final KakaoTokenService kakaoTokenService;
    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;
    private final JwtRefreshTokenService jwtRefreshTokenService;

    /**
     * 카카오 로그인 / 자동 회원가입
     *
     * 1. 카카오 Access Token으로 유저 정보 조회 (토큰 유효성 검증)
     * 2. 신규 유저면 users 테이블에 INSERT, 기존 유저면 조회
     * 3. 카카오 토큰 Redis에 저장 (알림 전송용)
     * 4. 자체 JWT 발급 + Refresh Token Redis 저장
     */
    @Transactional
    public LoginResponse login(KakaoLoginRequest request) {
        // 1. 카카오 유저 정보 조회 (토큰 유효성 검증 포함)
        KakaoUserInfoResponse userInfo = kakaoApiClient.getUserInfo(request.kakaoAccessToken());
        Long kakaoId = userInfo.getKakaoId();

        // 2. 신규 유저 자동 가입 / 기존 유저 조회
        User user = userRepository.findByKakaoId(kakaoId)
                .orElseGet(() -> {
                    log.info("New user registered. kakaoId={}", kakaoId);

                    return userRepository.save(User.builder()
                            .kakaoId(kakaoId)
                            .name(userInfo.getNickname() != null ? userInfo.getNickname() : "Unknown")
                            .build());
                });

        Long userId = user.getId().longValue();

        // 3. 카카오 토큰 Redis 저장 (알림 메시지 전송용)
        kakaoTokenService.saveTokens(userId, request.kakaoAccessToken(), request.kakaoRefreshToken());

        // 4. 자체 JWT 발급 + Refresh Token Redis 저장
        String accessToken  = jwtProvider.generateAccessToken(userId);
        String refreshToken = jwtProvider.generateRefreshToken(userId);
        jwtRefreshTokenService.save(userId, refreshToken);

        log.info("Login success. userId={}", userId);
        return new LoginResponse(accessToken, refreshToken);
    }

    /**
     * JWT Access Token 재발급
     *
     * 1. Refresh Token 서명/만료 검증
     * 2. Redis에 저장된 Refresh Token과 일치 여부 확인 (탈취 방지)
     * 3. 새 Access Token 발급
     */
    public String refresh(String refreshToken) {
        // 1. 토큰 유효성 검증
        if (!jwtProvider.validateToken(refreshToken)) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        Long userId = jwtProvider.getUserId(refreshToken);

        // 2. Redis 저장 토큰과 일치 여부 확인
        String storedToken = jwtRefreshTokenService.find(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN, "만료되었거나 로그아웃된 Refresh Token입니다."));

        if (!storedToken.equals(refreshToken)) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN, "Refresh Token이 일치하지 않습니다.");
        }

        // 3. 새 Access Token 발급
        log.info("Access token refreshed. userId={}", userId);
        return jwtProvider.generateAccessToken(userId);
    }
}

