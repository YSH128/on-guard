package com.shutter.api.user.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoginResponse {

    private final Boolean isNewUser;
    private final Long kakaoId;
    private final Long userId;
    private final String accessToken;
    private final String refreshToken;

    // 기존 유저
    public LoginResponse(Long userId, String accessToken, String refreshToken) {
        this.isNewUser = false;
        this.kakaoId = null;
        this.userId = userId;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
    }

    // 신규 유저
    public LoginResponse(Long kakaoId) {
        this.isNewUser = true;
        this.kakaoId = kakaoId;
        this.userId = null;
        this.accessToken = null;
        this.refreshToken = null;
    }
}
