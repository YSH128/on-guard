package com.shutter.api.user.controller;

import com.shutter.api.global.common.ApiResponse;
import com.shutter.api.user.dto.request.KakaoLoginRequest;
import com.shutter.api.user.dto.request.RegisterRequest;
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
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 카카오 로그인 / 신규 유저 확인
     */
    @PostMapping("/kakao")
    public ResponseEntity<ApiResponse<LoginResponse>> kakaoLogin(
            @Valid @RequestBody KakaoLoginRequest request
    ) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.of(response.getIsNewUser() ? "신규 유저입니다." : "로그인 성공", response));
    }

    /**
     * 신규 유저 회원가입
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<LoginResponse>> register(
            @Valid @RequestBody RegisterRequest request
    ) {
        LoginResponse response = authService.register(request);
        return ResponseEntity.ok(ApiResponse.of("회원가입 성공", response));
    }

    /**
     * JWT Access Token 재발급
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<String>> refresh(
            @Valid @RequestBody TokenRefreshRequest request
    ) {
        String newAccessToken = authService.refresh(request.refreshToken());
        return ResponseEntity.ok(ApiResponse.of("토큰 갱신 성공", newAccessToken));
    }
}
