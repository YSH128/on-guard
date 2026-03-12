package com.shutter.api.user.service;

import com.shutter.api.global.auth.kakao.KakaoApiClient;
import com.shutter.api.global.auth.kakao.KakaoUserInfoResponse;
import com.shutter.api.global.auth.token.KakaoTokenService;
import com.shutter.api.global.exception.BusinessException;
import com.shutter.api.global.exception.ErrorCode;
import com.shutter.api.global.jwt.JwtProvider;
import com.shutter.api.global.jwt.JwtRefreshTokenService;
import com.shutter.api.user.domain.User;
import com.shutter.api.user.dto.response.LoginResponse;
import com.shutter.api.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @InjectMocks
    private AuthService authService;

    @Mock private KakaoApiClient kakaoApiClient;
    @Mock private KakaoTokenService kakaoTokenService;
    @Mock private UserRepository userRepository;
    @Mock private JwtProvider jwtProvider;
    @Mock private JwtRefreshTokenService jwtRefreshTokenService;

    private static final String KAKAO_ACCESS_TOKEN  = "kakao-access-token";
    private static final String KAKAO_REFRESH_TOKEN = "kakao-refresh-token";
    private static final Long   KAKAO_ID            = 12345L;
    private static final Long   USER_ID             = 1L;

    // ───────────────────── login() ─────────────────────

    @Test
    @DisplayName("신규 유저 - 카카오 로그인 시 users 테이블에 저장되고 JWT가 발급된다")
    void login_newUser_savesAndReturnsJwt() {
        // given
        KakaoUserInfoResponse userInfo = mockKakaoUserInfo(KAKAO_ID);
        given(kakaoApiClient.getUserInfo(KAKAO_ACCESS_TOKEN)).willReturn(userInfo);
        given(userRepository.findByKakaoId(KAKAO_ID)).willReturn(Optional.empty());

        User savedUser = mockUser(USER_ID, KAKAO_ID);
        given(userRepository.save(any(User.class))).willReturn(savedUser);
        given(jwtProvider.generateAccessToken(USER_ID)).willReturn("access-token");
        given(jwtProvider.generateRefreshToken(USER_ID)).willReturn("refresh-token");

        // when
        LoginResponse response = authService.login(new com.shutter.api.user.dto.request.KakaoLoginRequest(KAKAO_ACCESS_TOKEN, KAKAO_REFRESH_TOKEN, com.shutter.api.user.domain.Role.WARD));

        // then
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        then(userRepository).should(times(1)).save(any(User.class));
        then(kakaoTokenService).should(times(1)).saveTokens(USER_ID, KAKAO_ACCESS_TOKEN, KAKAO_REFRESH_TOKEN);
        then(jwtRefreshTokenService).should(times(1)).save(USER_ID, "refresh-token");
    }

    @Test
    @DisplayName("기존 유저 - 재로그인 시 users 테이블에 저장하지 않고 JWT가 발급된다")
    void login_existingUser_doesNotSaveAndReturnsJwt() {
        // given
        KakaoUserInfoResponse userInfo = mockKakaoUserInfo(KAKAO_ID);
        given(kakaoApiClient.getUserInfo(KAKAO_ACCESS_TOKEN)).willReturn(userInfo);

        User existingUser = mockUser(USER_ID, KAKAO_ID);
        given(userRepository.findByKakaoId(KAKAO_ID)).willReturn(Optional.of(existingUser));
        given(jwtProvider.generateAccessToken(USER_ID)).willReturn("access-token");
        given(jwtProvider.generateRefreshToken(USER_ID)).willReturn("refresh-token");

        // when
        LoginResponse response = authService.login(new com.shutter.api.user.dto.request.KakaoLoginRequest(KAKAO_ACCESS_TOKEN, KAKAO_REFRESH_TOKEN, com.shutter.api.user.domain.Role.WARD));

        // then
        assertThat(response.accessToken()).isEqualTo("access-token");
        then(userRepository).should(times(0)).save(any(User.class));  // INSERT 없음
    }

    // ───────────────────── refresh() ─────────────────────

    @Test
    @DisplayName("유효한 Refresh Token으로 새 Access Token이 발급된다")
    void refresh_validToken_returnsNewAccessToken() {
        // given
        String refreshToken = "valid-refresh-token";
        given(jwtProvider.validateToken(refreshToken)).willReturn(true);
        given(jwtProvider.getUserId(refreshToken)).willReturn(USER_ID);
        given(jwtRefreshTokenService.find(USER_ID)).willReturn(Optional.of(refreshToken));
        given(jwtProvider.generateAccessToken(USER_ID)).willReturn("new-access-token");

        // when
        String newAccessToken = authService.refresh(refreshToken);

        // then
        assertThat(newAccessToken).isEqualTo("new-access-token");
    }

    @Test
    @DisplayName("만료된 Refresh Token이면 INVALID_REFRESH_TOKEN 예외가 발생한다")
    void refresh_invalidToken_throwsException() {
        // given
        String expiredToken = "expired-refresh-token";
        given(jwtProvider.validateToken(expiredToken)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> authService.refresh(expiredToken))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN));
    }

    @Test
    @DisplayName("Redis에 없는 Refresh Token이면 INVALID_REFRESH_TOKEN 예외가 발생한다")
    void refresh_tokenNotInRedis_throwsException() {
        // given
        String refreshToken = "not-in-redis-token";
        given(jwtProvider.validateToken(refreshToken)).willReturn(true);
        given(jwtProvider.getUserId(refreshToken)).willReturn(USER_ID);
        given(jwtRefreshTokenService.find(USER_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> authService.refresh(refreshToken))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN));
    }

    // ───────────────────── helpers ─────────────────────

    private KakaoUserInfoResponse mockKakaoUserInfo(Long kakaoId) {
        KakaoUserInfoResponse response = new KakaoUserInfoResponse();
        // KakaoUserInfoResponse는 @JsonIgnoreProperties 사용 중이므로 리플렉션으로 설정
        try {
            var field = KakaoUserInfoResponse.class.getDeclaredField("kakaoId");
            field.setAccessible(true);
            field.set(response, kakaoId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return response;
    }

    private User mockUser(Long userId, Long kakaoId) {
        User user = User.builder()
                .kakaoId(kakaoId)
                .name("test-name")
                .role(com.shutter.api.user.domain.Role.WARD)
                .build();
        try {
            var field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, userId.intValue());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return user;
    }
}
