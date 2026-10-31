package com.shutter.api.user.service;

import com.shutter.api.global.auth.kakao.KakaoApiClient;
import com.shutter.api.global.auth.kakao.KakaoTokenInfoResponse;
import com.shutter.api.global.auth.kakao.KakaoUserInfoResponse;
import com.shutter.api.global.auth.token.KakaoTokenService;
import com.shutter.api.global.exception.BusinessException;
import com.shutter.api.global.exception.ErrorCode;
import com.shutter.api.global.jwt.JwtProvider;
import com.shutter.api.global.jwt.JwtRefreshTokenService;
import com.shutter.api.user.domain.Role;
import com.shutter.api.user.domain.User;
import com.shutter.api.user.dto.request.KakaoLoginRequest;
import com.shutter.api.user.dto.response.LoginResponse;
import com.shutter.api.user.dto.response.TokenRefreshResult;
import com.shutter.api.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @InjectMocks
    private AuthService authService;

    @Mock
    private KakaoApiClient kakaoApiClient;
    @Mock
    private KakaoTokenService kakaoTokenService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private JwtProvider jwtProvider;
    @Mock
    private JwtRefreshTokenService jwtRefreshTokenService;

    private static final String KAKAO_ACCESS_TOKEN = "kakao-access-token";
    private static final String KAKAO_REFRESH_TOKEN = "kakao-refresh-token";
    private static final String DEVICE_UUID = "device-uuid-1234";
    private static final Long KAKAO_ID = 12345L;
    private static final Long USER_ID = 1L;
    private static final String USER_NAME = "test-user";

    // ───────────────────── login() ─────────────────────

    @Test
    @DisplayName("신규 유저 - 카카오 이름으로 자동 가입 후 JWT를 발급한다")
    void login_newUser_autoRegistersAndReturnsJwt() {
        // given
        KakaoLoginRequest request = mockKakaoLoginRequest();
        KakaoUserInfoResponse userInfo = mockKakaoUserInfo(KAKAO_ID, USER_NAME);
        given(kakaoApiClient.getUserInfo(KAKAO_ACCESS_TOKEN)).willReturn(userInfo);
        given(userRepository.findByKakaoId(KAKAO_ID)).willReturn(Optional.empty());

        // save() 호출 시 JPA @GeneratedValue처럼 ID 주입
        given(userRepository.save(any(User.class))).willAnswer(invocation -> {
            User u = invocation.getArgument(0);
            setField(u, "id", USER_ID);
            return u;
        });

        given(jwtProvider.generateAccessToken(eq(USER_ID), any())).willReturn("access-token");
        given(jwtProvider.generateRefreshToken(USER_ID)).willReturn("refresh-token");

        // when
        LoginResponse response = authService.login(request);

        // then
        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getUserId()).isEqualTo(USER_ID);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        then(userRepository).should(times(1)).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getKakaoId()).isEqualTo(KAKAO_ID);
        assertThat(savedUser.getName()).isEqualTo(USER_NAME);
        assertThat(savedUser.getDeviceUuid()).isEqualTo(DEVICE_UUID);
    }

    @Test
    @DisplayName("기존 유저 - 로그인 시 JWT를 발급하고 deviceUuid를 업데이트한다")
    void login_existingUser_returnsJwtAndUpdatesUuid() {
        // given
        KakaoLoginRequest request = mockKakaoLoginRequest();
        KakaoUserInfoResponse userInfo = mockKakaoUserInfo(KAKAO_ID, USER_NAME);
        given(kakaoApiClient.getUserInfo(KAKAO_ACCESS_TOKEN)).willReturn(userInfo);

        User existingUser = mockUser(USER_ID, KAKAO_ID, "old-uuid");
        given(userRepository.findByKakaoId(KAKAO_ID)).willReturn(Optional.of(existingUser));
        given(jwtProvider.generateAccessToken(eq(USER_ID), any())).willReturn("access-token");
        given(jwtProvider.generateRefreshToken(USER_ID)).willReturn("refresh-token");

        // when
        LoginResponse response = authService.login(request);

        // then
        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getUserId()).isEqualTo(USER_ID);
        then(userRepository).should(times(0)).save(any(User.class));
        assertThat(existingUser.getDeviceUuid()).isEqualTo(DEVICE_UUID);
    }

    @Test
    @DisplayName("App ID 검증 활성화 시 다른 앱의 토큰이면 예외를 발생시킨다")
    void login_appIdMismatch_throwsException() {
        // given
        ReflectionTestUtils.setField(authService, "expectedAppId", "999999");
        KakaoLoginRequest request = mockKakaoLoginRequest();
        KakaoTokenInfoResponse tokenInfo = new KakaoTokenInfoResponse(KAKAO_ID, 3600, 111111L);
        given(kakaoApiClient.getTokenInfo(KAKAO_ACCESS_TOKEN)).willReturn(tokenInfo);

        // when & then
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_KAKAO_APP_TOKEN);
    }

    // ───────────────────── refresh() ─────────────────────

    @Test
    @DisplayName("유효한 Refresh Token으로 요청 시 토큰 세트가 회전 발급된다 (RTR)")
    void refresh_success_rotatesTokens_and_savesToRedis() {
        // given
        String oldRefreshToken = "valid-refresh-token";
        User user = mockUser(USER_ID, KAKAO_ID, DEVICE_UUID);

        given(jwtProvider.validateToken(oldRefreshToken)).willReturn(true);
        given(jwtProvider.getUserId(oldRefreshToken)).willReturn(USER_ID);
        given(jwtRefreshTokenService.find(USER_ID)).willReturn(Optional.of(oldRefreshToken));
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(jwtProvider.generateAccessToken(eq(USER_ID), any())).willReturn("new-access-token");
        given(jwtProvider.generateRefreshToken(USER_ID)).willReturn("new-refresh-token");

        // when
        TokenRefreshResult result = authService.refresh(oldRefreshToken);

        // then
        assertThat(result.newAccessToken()).isEqualTo("new-access-token");
        assertThat(result.newRefreshToken()).isEqualTo("new-refresh-token");
        then(jwtRefreshTokenService).should(times(1)).save(USER_ID, "new-refresh-token");
    }

    @Test
    @DisplayName("서명이 잘못되었거나 유효하지 않은 Refresh Token이면 예외를 던진다")
    void refresh_invalidSignature_throwsException() {
        // given
        String invalidToken = "invalid-token";
        given(jwtProvider.validateToken(invalidToken)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> authService.refresh(invalidToken))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    @Test
    @DisplayName("이미 사용되었거나 Redis 토큰과 불일치할 경우 탈취로 간주해 세션을 파기한다 (재사용 감지)")
    void refresh_reuseDetected_revokesSession_and_throwsException() {
        // given
        String reusedToken = "already-used-or-stolen-token";
        given(jwtProvider.validateToken(reusedToken)).willReturn(true);
        given(jwtProvider.getUserId(reusedToken)).willReturn(USER_ID);
        given(jwtRefreshTokenService.find(USER_ID)).willReturn(Optional.of("current-latest-token"));

        // when & then
        assertThatThrownBy(() -> authService.refresh(reusedToken))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REUSE_DETECTED_REFRESH_TOKEN);

        // Redis 세션이 즉시 삭제되었는지 검증
        then(jwtRefreshTokenService).should(times(1)).delete(USER_ID);
    }

    // ───────────────────── helpers ─────────────────────

    private KakaoLoginRequest mockKakaoLoginRequest() {
        KakaoLoginRequest request = new KakaoLoginRequest();
        setField(request, "kakaoAccessToken", KAKAO_ACCESS_TOKEN);
        setField(request, "kakaoRefreshToken", KAKAO_REFRESH_TOKEN);
        setField(request, "deviceUuid", DEVICE_UUID);
        return request;
    }

    private KakaoUserInfoResponse mockKakaoUserInfo(Long kakaoId, String name) {
        KakaoUserInfoResponse response = new KakaoUserInfoResponse();
        setField(response, "kakaoId", kakaoId);
        KakaoUserInfoResponse.KakaoAccount account = new KakaoUserInfoResponse.KakaoAccount();
        setField(account, "name", name);
        setField(response, "kakaoAccount", account);
        return response;
    }

    private User mockUser(Long userId, Long kakaoId, String deviceUuid) {
        User user = User.builder()
                .kakaoId(kakaoId)
                .name("test-name")
                .deviceUuid(deviceUuid)
                .build();
        user.assignRole(Role.WARD);
        setField(user, "id", userId);
        return user;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field: " + fieldName, e);
        }
    }
}
