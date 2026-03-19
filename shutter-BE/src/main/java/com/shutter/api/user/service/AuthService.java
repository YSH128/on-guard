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
import com.shutter.api.user.dto.request.RegisterRequest;
import com.shutter.api.user.dto.response.LoginResponse;
import com.shutter.api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

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
     * 카카오 로그인 / 신규 유저 확인
     *
     * 1. 카카오 Access Token으로 유저 정보 조회 (토큰 유효성 검증)
     * 2. DB에서 유저 조회
     * 3. 기존 유저면 로그인 처리, 신규 유저면 isNewUser: true 반환
     */
    @Transactional
    public LoginResponse login(KakaoLoginRequest request) {
        // 1. 카카오 유저 정보 조회
        KakaoUserInfoResponse userInfo = kakaoApiClient.getUserInfo(request.getKakaoAccessToken());
        Long kakaoId = userInfo.getKakaoId();

        // 2. DB에서 유저 조회
        Optional<User> userOptional = userRepository.findByKakaoId(kakaoId);

        // 3. 분기 처리
        if (userOptional.isPresent()) {
            // 기존 유저 로그인
            User user = userOptional.get();
            user.updateDeviceUuid(request.getDeviceUuid());
            Long userId = user.getId();

            // 카카오 토큰 저장 (알림용)
            kakaoTokenService.saveTokens(userId, request.getKakaoAccessToken(), request.getKakaoRefreshToken());

            // JWT 발급
            String accessToken  = jwtProvider.generateAccessToken(userId);
            String refreshToken = jwtProvider.generateRefreshToken(userId);
            jwtRefreshTokenService.save(userId, refreshToken);

            log.info("Login success. userId={}", userId);
            return new LoginResponse(userId, accessToken, refreshToken);
        } else {
            // 신규 유저
            log.info("New user detected. kakaoId={}", kakaoId);
            // 회원가입 단계에서 카카오 토큰을 다시 받을 필요 없으므로 여기서 임시 저장
            // key를 userId 대신 kakaoId로 저장
            kakaoTokenService.saveTokens(kakaoId, request.getKakaoAccessToken(), request.getKakaoRefreshToken());
            return new LoginResponse(kakaoId);
        }
    }

    /**
     * 신규 유저 회원가입
     */
    @Transactional
    public LoginResponse register(RegisterRequest request) {
        Long kakaoId = request.getKakaoId();
        if (userRepository.findByKakaoId(kakaoId).isPresent()) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS);
        }

        // 유저 생성 및 저장
        User user = User.builder()
                .kakaoId(kakaoId)
                .name(request.getName())
                .deviceUuid(request.getDeviceUuid())
                .build();
        userRepository.save(user);

        Long userId = user.getId();
        log.info("New user registered. userId={}, kakaoId={}", userId, kakaoId);

        // 임시 저장된 카카오 토큰을 userId 키로 다시 저장
        Optional<String> accessTokenOpt = kakaoTokenService.getAccessToken(kakaoId);
        Optional<String> refreshTokenOpt = kakaoTokenService.getRefreshToken(kakaoId);

        if (accessTokenOpt.isPresent() && refreshTokenOpt.isPresent()) {
            kakaoTokenService.saveTokens(userId, accessTokenOpt.get(), refreshTokenOpt.get());
            kakaoTokenService.deleteTokens(kakaoId); // 임시 토큰 삭제
            log.info("Migrated kakao tokens from kakaoId:{} to userId:{}", kakaoId, userId);
        } else {
            log.warn("Could not find temporary kakao tokens for kakaoId: {}. Notification features might not work.", kakaoId);
        }

        // JWT 발급
        String accessToken  = jwtProvider.generateAccessToken(userId);
        String refreshToken = jwtProvider.generateRefreshToken(userId);
        jwtRefreshTokenService.save(userId, refreshToken);

        return new LoginResponse(userId, accessToken, refreshToken);
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

    /**
     * [테스트용] 특정 유저 ID로 즉시 JWT 발급 (로컬 전용)
     */
    public LoginResponse generateTestToken(Long userId) {
        String accessToken = jwtProvider.generateAccessToken(userId);
        String refreshToken = jwtProvider.generateRefreshToken(userId);
        jwtRefreshTokenService.save(userId, refreshToken);
        return new LoginResponse(userId, accessToken, refreshToken);
    }
}

