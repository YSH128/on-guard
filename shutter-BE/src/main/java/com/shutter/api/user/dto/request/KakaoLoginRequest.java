package com.shutter.api.user.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record KakaoLoginRequest(
        @NotBlank @JsonProperty("kakao_access_token") String kakaoAccessToken,
        @NotBlank @JsonProperty("kakao_refresh_token") String kakaoRefreshToken
) {}
