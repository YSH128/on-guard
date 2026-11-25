package com.shutter.api.user.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.shutter.api.global.common.ApiResponse;
import com.shutter.api.global.jwt.JwtProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Swagger 및 로컬 테스트용 인증 우회 컨트롤러
 * (local 프로필에서만 활성화됨)
 */
@Tag(name = "Test Auth API", description = "로컬 테스트용 JWT 토큰 발급 (운영 서버 비활성화)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/test")
@Profile({"local", "bench"})
public class TestAuthController {

    private final JwtProvider jwtProvider;

    @Operation(summary = "테스트용 JWT 발급", description = "사용자 ID(PK)만으로 임의의 Access Token과 Refresh Token을 발급합니다.")
    @GetMapping("/token/{userId}")
    public ResponseEntity<ApiResponse<TestTokenResponse>> getTestToken(@PathVariable("userId") Long userId) {
        String accessToken = jwtProvider.generateAccessToken(userId);
        String refreshToken = jwtProvider.generateRefreshToken(userId);

        return ResponseEntity.ok(ApiResponse.of("테스트 토큰 발급 성공", new TestTokenResponse(accessToken, refreshToken)));
    }

    @Getter
    public static class TestTokenResponse {
        @JsonProperty("access_token")
        private final String accessToken;

        @JsonProperty("refresh_token")
        private final String refreshToken;

        public TestTokenResponse(String accessToken, String refreshToken) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
        }
    }
}
