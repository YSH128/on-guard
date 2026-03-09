package com.shutter.api.user.controller;

import com.shutter.api.global.common.ApiResponse;
import com.shutter.api.user.dto.request.KakaoLoginRequest;
import com.shutter.api.user.dto.request.TokenRefreshRequest;
import com.shutter.api.user.dto.response.LoginResponse;
import com.shutter.api.user.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 카카오 로그인 / 자동 회원가입
     *
     * 안드로이드 앱이 카카오 SDK로 발급받은 토큰을 전달하면
     * 서버가 검증 후 자체 JWT를 발급하여 반환한다.
     */
    @PostMapping("/kakao")
    public ResponseEntity<ApiResponse<LoginResponse>> kakaoLogin(
            @Valid @RequestBody KakaoLoginRequest request
    ) {
        LoginResponse response = authService.login(
                request.kakaoAccessToken(),
                request.kakaoRefreshToken()
        );
        return ResponseEntity.ok(ApiResponse.of("로그인 성공", response));
    }

    /**
     * JWT Access Token 재발급
     *
     * Refresh Token이 유효하고 Redis에 저장된 값과 일치하면 새 Access Token 반환.
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<String>> refresh(
            @Valid @RequestBody TokenRefreshRequest request
    ) {
        String newAccessToken = authService.refresh(request.refreshToken());
        return ResponseEntity.ok(ApiResponse.of("토큰 갱신 성공", newAccessToken));
    }
}
