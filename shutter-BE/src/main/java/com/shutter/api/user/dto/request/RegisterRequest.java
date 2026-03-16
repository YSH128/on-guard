package com.shutter.api.user.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class RegisterRequest {
    @NotNull
    @JsonProperty("kakao_id")
    private Long kakaoId;

    @NotBlank
    private String name;

    @NotBlank
    @JsonProperty("device_uuid")
    private String deviceUuid;
}
