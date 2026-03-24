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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
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
    @DisplayName("신규 유저 - 로그인 시 isNewUser 플래그와 카카오 ID를 반환한다")
    void login_newUser_returnsIsNewUserFlag() {
        // given
        KakaoLoginRequest request = mockKakaoLoginRequest();
        KakaoUserInfoResponse userInfo = mockKakaoUserInfo(KAKAO_ID);
        given(kakaoApiClient.getUserInfo(KAKAO_ACCESS_TOKEN)).willReturn(userInfo);
        given(userRepository.findByKakaoId(KAKAO_ID)).willReturn(Optional.empty());

        // when
        LoginResponse response = authService.login(request);

        // then
        assertThat(response.getIsNewUser()).isTrue();
        assertThat(response.getKakaoId()).isEqualTo(KAKAO_ID);
        assertThat(response.getUserId()).isNull();
        assertThat(response.getAccessToken()).isNull();
        then(userRepository).should(times(0)).save(any(User.class));
        then(kakaoTokenService).should(times(1)).saveTokens(KAKAO_ID, KAKAO_ACCESS_TOKEN, KAKAO_REFRESH_TOKEN);
    }

    @Test
    @DisplayName("기존 유저 - 로그인 시 JWT를 발급하고 deviceUuid를 업데이트한다")
    void login_existingUser_returnsJwtAndUpdatesUuid() {
        // given
        KakaoLoginRequest request = mockKakaoLoginRequest();
        KakaoUserInfoResponse userInfo = mockKakaoUserInfo(KAKAO_ID);
        given(kakaoApiClient.getUserInfo(KAKAO_ACCESS_TOKEN)).willReturn(userInfo);

        User existingUser = mockUser(USER_ID, KAKAO_ID, "old-uuid");
        given(userRepository.findByKakaoId(KAKAO_ID)).willReturn(Optional.of(existingUser));
        given(jwtProvider.generateAccessToken(USER_ID)).willReturn("access-token");
        given(jwtProvider.generateRefreshToken(USER_ID)).willReturn("refresh-token");

        // when
        LoginResponse response = authService.login(request);

        // then
        assertThat(response.getIsNewUser()).isFalse();
        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getUserId()).isEqualTo(USER_ID);
        then(userRepository).should(times(0)).save(any(User.class));
        assertThat(existingUser.getDeviceUuid()).isEqualTo(DEVICE_UUID); // verify uuid update
    }

    // ───────────────────── register() ─────────────────────

    @Test
    @DisplayName("신규 유저 - 회원가입 시 유저를 생성하고 JWT를 발급한다")
    void register_newUser_createsUserAndReturnsJwt() {
        // given
        RegisterRequest request = mockRegisterRequest();
        given(userRepository.findByKakaoId(KAKAO_ID)).willReturn(Optional.empty());
        given(jwtProvider.generateAccessToken(any(Long.class))).willReturn("access-token");
        given(jwtProvider.generateRefreshToken(any(Long.class))).willReturn("refresh-token");

        // mock kakao token migration
        given(kakaoTokenService.getAccessToken(KAKAO_ID)).willReturn(Optional.of(KAKAO_ACCESS_TOKEN));
        given(kakaoTokenService.getRefreshToken(KAKAO_ID)).willReturn(Optional.of(KAKAO_REFRESH_TOKEN));

        // when
        authService.register(request);

        // then
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        then(userRepository).should(times(1)).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getKakaoId()).isEqualTo(KAKAO_ID);
        assertThat(savedUser.getName()).isEqualTo(USER_NAME);
        assertThat(savedUser.getDeviceUuid()).isEqualTo(DEVICE_UUID);

        then(kakaoTokenService).should(times(1)).saveTokens(any(Long.class), any(), any());
        then(kakaoTokenService).should(times(1)).deleteTokens(KAKAO_ID);
        then(jwtRefreshTokenService).should(times(1)).save(any(Long.class), any());
    }

    @Test
    @DisplayName("회원가입 - 이미 존재하는 유저일 경우 예외를 발생시킨다")
    void register_existingUser_throwsException() {
        // given
        RegisterRequest request = mockRegisterRequest();
        given(userRepository.findByKakaoId(KAKAO_ID)).willReturn(Optional.of(mockUser(USER_ID, KAKAO_ID, DEVICE_UUID)));

        // when & then
        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.USER_ALREADY_EXISTS));
    }


    // ───────────────────── helpers ─────────────────────

    private KakaoLoginRequest mockKakaoLoginRequest() {
        KakaoLoginRequest request = new KakaoLoginRequest();
        setField(request, "kakaoAccessToken", KAKAO_ACCESS_TOKEN);
        setField(request, "kakaoRefreshToken", KAKAO_REFRESH_TOKEN);
        setField(request, "deviceUuid", DEVICE_UUID);
        return request;
    }

    private RegisterRequest mockRegisterRequest() {
        RegisterRequest request = new RegisterRequest();
        setField(request, "kakaoId", KAKAO_ID);
        setField(request, "name", USER_NAME);
        setField(request, "deviceUuid", DEVICE_UUID);
        return request;
    }

    private KakaoUserInfoResponse mockKakaoUserInfo(Long kakaoId) {
        KakaoUserInfoResponse response = new KakaoUserInfoResponse();
        setField(response, "kakaoId", kakaoId);
        return response;
    }

    private User mockUser(Long userId, Long kakaoId, String deviceUuid) {
        User user = User.builder()
                .kakaoId(kakaoId)
                .name("test-name")
                .deviceUuid(deviceUuid)
                .build();
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

