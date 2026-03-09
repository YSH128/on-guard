package com.shutter.api.user.service;

import com.shutter.api.global.auth.kakao.KakaoApiClient;
import com.shutter.api.global.auth.kakao.KakaoUserInfoResponse;
import com.shutter.api.global.auth.token.KakaoTokenService;
import com.shutter.api.global.jwt.JwtProvider;
import com.shutter.api.user.domain.User;
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

    /**
     * 카카오 로그인 / 자동 회원가입
     *
     * 1. 카카오 Access Token으로 유저 정보 조회 (토큰 유효성 검증)
     * 2. 신규 유저면 users 테이블에 INSERT, 기존 유저면 조회
     * 3. 카카오 토큰 Redis에 저장 (알림 전송용)
     * 4. 자체 JWT 발급 후 반환
     */
    @Transactional
    public LoginResponse login(String kakaoAccessToken, String kakaoRefreshToken) {
        // 1. 카카오 유저 정보 조회 (토큰 유효성 검증 포함)
        KakaoUserInfoResponse userInfo = kakaoApiClient.getUserInfo(kakaoAccessToken);
        String kakaoId = String.valueOf(userInfo.getKakaoId());

        // 2. 신규 유저 자동 가입 / 기존 유저 조회
        User user = userRepository.findByKakaoId(kakaoId)
                .orElseGet(() -> {
                    log.info("New user registered. kakaoId={}", kakaoId);
                    return userRepository.save(User.builder()
                            .kakaoId(kakaoId)
                            .build());
                });

        // 3. 카카오 토큰 Redis 저장 (알림 메시지 전송용)
        kakaoTokenService.saveTokens(user.getId().longValue(), kakaoAccessToken, kakaoRefreshToken);

        // 4. 자체 JWT 발급
        String accessToken = jwtProvider.generateAccessToken(user.getId().longValue());
        String refreshToken = jwtProvider.generateRefreshToken(user.getId().longValue());

        log.info("Login success. userId={}", user.getId());
        return new LoginResponse(accessToken, refreshToken);
    }
}
