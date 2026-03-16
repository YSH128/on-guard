package com.shutter.api.user.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class KakaoLoginRequest {
    @NotBlank @JsonProperty("kakao_access_token")
    private String kakaoAccessToken;
    @NotBlank @JsonProperty("kakao_refresh_token")
    private String kakaoRefreshToken;
    @NotBlank @JsonProperty("device_uuid")
    private String deviceUuid;
}
