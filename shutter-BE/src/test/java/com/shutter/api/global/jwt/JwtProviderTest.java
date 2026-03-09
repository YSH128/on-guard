package com.shutter.api.global.jwt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class JwtProviderTest {

    private JwtProvider jwtProvider;

    private static final String SECRET    = "test-secret-key-must-be-at-least-32-characters-long";
    private static final long   ACCESS_MS = 3_600_000L;   // 1시간
    private static final long   REFRESH_MS = 1_209_600_000L; // 14일

    @BeforeEach
    void setUp() {
        jwtProvider = new JwtProvider();
        ReflectionTestUtils.setField(jwtProvider, "secretValue", SECRET);
        ReflectionTestUtils.setField(jwtProvider, "accessExpiryMs", ACCESS_MS);
        ReflectionTestUtils.setField(jwtProvider, "refreshExpiryMs", REFRESH_MS);
        jwtProvider.init();
    }

    @Test
    @DisplayName("Access Token 발급 후 userId를 정상적으로 추출할 수 있다")
    void generateAccessToken_and_getUserId() {
        // given
        Long userId = 1L;

        // when
        String token = jwtProvider.generateAccessToken(userId);

        // then
        assertThat(token).isNotBlank();
        assertThat(jwtProvider.getUserId(token)).isEqualTo(userId);
    }

    @Test
    @DisplayName("Refresh Token 발급 후 userId를 정상적으로 추출할 수 있다")
    void generateRefreshToken_and_getUserId() {
        // given
        Long userId = 42L;

        // when
        String token = jwtProvider.generateRefreshToken(userId);

        // then
        assertThat(token).isNotBlank();
        assertThat(jwtProvider.getUserId(token)).isEqualTo(userId);
    }

    @Test
    @DisplayName("유효한 토큰은 validateToken이 true를 반환한다")
    void validateToken_validToken_returnsTrue() {
        // given
        String token = jwtProvider.generateAccessToken(1L);

        // when & then
        assertThat(jwtProvider.validateToken(token)).isTrue();
    }

    @Test
    @DisplayName("잘못된 시크릿으로 서명된 토큰은 validateToken이 false를 반환한다")
    void validateToken_wrongSignature_returnsFalse() {
        // given - 다른 시크릿으로 만든 JwtProvider로 토큰 생성
        JwtProvider other = new JwtProvider();
        ReflectionTestUtils.setField(other, "secretValue", "another-secret-key-that-is-different-32chars!!");
        ReflectionTestUtils.setField(other, "accessExpiryMs", ACCESS_MS);
        ReflectionTestUtils.setField(other, "refreshExpiryMs", REFRESH_MS);
        other.init();

        String token = other.generateAccessToken(1L);

        // when & then
        assertThat(jwtProvider.validateToken(token)).isFalse();
    }

    @Test
    @DisplayName("만료된 토큰은 validateToken이 false를 반환한다")
    void validateToken_expiredToken_returnsFalse() {
        // given - TTL 0으로 즉시 만료되는 토큰 생성
        JwtProvider expiredProvider = new JwtProvider();
        ReflectionTestUtils.setField(expiredProvider, "secretValue", SECRET);
        ReflectionTestUtils.setField(expiredProvider, "accessExpiryMs", 0L);
        ReflectionTestUtils.setField(expiredProvider, "refreshExpiryMs", REFRESH_MS);
        expiredProvider.init();

        String token = expiredProvider.generateAccessToken(1L);

        // when & then
        assertThat(jwtProvider.validateToken(token)).isFalse();
    }

    @Test
    @DisplayName("빈 문자열 토큰은 validateToken이 false를 반환한다")
    void validateToken_emptyToken_returnsFalse() {
        assertThat(jwtProvider.validateToken("")).isFalse();
    }
}
