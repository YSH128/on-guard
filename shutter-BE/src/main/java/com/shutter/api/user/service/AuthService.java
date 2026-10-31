package com.shutter.api.user.service;

import com.shutter.api.global.auth.kakao.KakaoApiClient;
import com.shutter.api.global.auth.kakao.KakaoTokenInfoResponse;
import com.shutter.api.global.auth.kakao.KakaoUserInfoResponse;
import com.shutter.api.global.auth.token.KakaoTokenService;
import com.shutter.api.global.exception.BusinessException;
import com.shutter.api.global.exception.ErrorCode;
import com.shutter.api.global.jwt.JwtProvider;
import com.shutter.api.global.jwt.JwtRefreshTokenService;
import com.shutter.api.user.domain.User;
import com.shutter.api.user.dto.request.KakaoLoginRequest;
import com.shutter.api.user.dto.response.LoginResponse;
import com.shutter.api.user.dto.response.TokenRefreshResult;
import com.shutter.api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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

    @Value("${kakao.app-id:}")
    private String expectedAppId;

    /**
     * 카카오 로그인 (신규 유저 자동 가입 포함)
     *
     * 1. (선택적) 카카오 토큰 정보 조회 및 타겟 App ID 일치 여부 검증 (토큰 탈취/위조 방지)
     * 2. 카카오 Access Token으로 유저 정보 조회
     * 3. DB에서 유저 조회
     * 4. 기존 유저면 로그인, 신규 유저면 카카오 이름으로 자동 가입 후 로그인
     */
    @Transactional
    public LoginResponse login(KakaoLoginRequest request) {
        // 0. 타겟 앱 토큰 검증 (설정되어 있을 경우)
        if (StringUtils.hasText(expectedAppId)) {
            KakaoTokenInfoResponse tokenInfo = kakaoApiClient.getTokenInfo(request.getKakaoAccessToken());
            if (tokenInfo.getAppId() == null || !expectedAppId.equals(String.valueOf(tokenInfo.getAppId()))) {
                log.warn("Kakao token appId mismatch. expected={}, actual={}", expectedAppId, tokenInfo.getAppId());
                throw new BusinessException(ErrorCode.INVALID_KAKAO_APP_TOKEN);
            }
        }

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
            user.updateProfileImageUrl(userInfo.getProfileImageUrl()); // Update profile image URL
            Long userId = user.getId();

            // 카카오 토큰 저장 (알림용)
            kakaoTokenService.saveTokens(userId, request.getKakaoAccessToken(), request.getKakaoRefreshToken());

            // JWT 발급 (Role 클레임 포함)
            String accessToken = jwtProvider.generateAccessToken(userId, user.getRole());
            String refreshToken = jwtProvider.generateRefreshToken(userId);
            jwtRefreshTokenService.save(userId, refreshToken);

            log.info("Login success. userId={}", userId);
            return new LoginResponse(userId, accessToken, refreshToken);
        } else {
            // 신규 유저 — 카카오 이름으로 자동 가입
            String kakaoName = userInfo.getName();
            if (kakaoName == null || kakaoName.isBlank()) {
                throw new BusinessException(ErrorCode.KAKAO_NAME_REQUIRED);
            }

            User user = User.builder()
                    .kakaoId(kakaoId)
                    .name(kakaoName)
                    .deviceUuid(request.getDeviceUuid())
                    .profileImageUrl(userInfo.getProfileImageUrl()) // Save profile image URL
                    .build();
            userRepository.save(user);

            Long userId = user.getId();
            log.info("New user auto-registered. userId={}, kakaoId={}", userId, kakaoId);

            // 카카오 토큰 저장 (알림용)
            kakaoTokenService.saveTokens(userId, request.getKakaoAccessToken(), request.getKakaoRefreshToken());

            // JWT 발급 (Role 클레임 포함)
            String accessToken = jwtProvider.generateAccessToken(userId, user.getRole());
            String refreshToken = jwtProvider.generateRefreshToken(userId);
            jwtRefreshTokenService.save(userId, refreshToken);

            return new LoginResponse(userId, accessToken, refreshToken);
        }
    }

    /**
     * JWT Access & Refresh Token 갱신 (RTR + 재사용 감지)
     *
     * 1. Refresh Token 서명/만료 검증
     * 2. Redis에 저장된 Refresh Token과 일치 여부 확인 (토큰 탈취 및 재사용 감지)
     *    - 불일치 시: 이미 사용되었거나 비정상적인 토큰이므로 Redis 세션 즉시 파기 및 예외 발생
     * 3. 유저 최신 정보 조회 (Role 반영)
     * 4. 새 Access Token & 새 Refresh Token 발급 및 Redis 갱신 (RTR)
     */
    @Transactional
    public TokenRefreshResult refresh(String refreshToken) {
        // 1. 토큰 유효성 검증
        if (!jwtProvider.validateToken(refreshToken)) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        Long userId = jwtProvider.getUserId(refreshToken);

        // 2. Redis 저장 토큰과 일치 여부 확인 (탈취/재사용 감지)
        Optional<String> storedTokenOpt = jwtRefreshTokenService.find(userId);
        if (storedTokenOpt.isEmpty() || !storedTokenOpt.get().equals(refreshToken)) {
            // 이미 사용되었거나 무효화된 토큰으로 재발급 시도 시 탈취로 간주하여 전체 세션 파기
            jwtRefreshTokenService.delete(userId);
            log.warn("Refresh token reuse or invalid access detected for userId={}. Revoking session.", userId);
            throw new BusinessException(ErrorCode.REUSE_DETECTED_REFRESH_TOKEN);
        }

        // 3. 유저 권한 정보 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 4. 새 토큰 세트 발급 (RTR)
        String newAccessToken = jwtProvider.generateAccessToken(userId, user.getRole());
        String newRefreshToken = jwtProvider.generateRefreshToken(userId);

        // Redis 저장소 회전 갱신
        jwtRefreshTokenService.save(userId, newRefreshToken);

        log.info("Tokens rotated successfully (RTR). userId={}", userId);
        return new TokenRefreshResult(newAccessToken, newRefreshToken);
    }
}
