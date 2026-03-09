package com.shutter.api.user.dto.request;

import jakarta.validation.constraints.NotBlank;

public record KakaoLoginRequest(
        @NotBlank String kakaoAccessToken,
        @NotBlank String kakaoRefreshToken
) {}
