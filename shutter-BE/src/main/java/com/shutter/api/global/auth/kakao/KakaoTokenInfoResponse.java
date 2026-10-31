package com.shutter.api.global.auth.kakao;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 카카오 토큰 정보 조회 응답 DTO
 * GET https://kapi.kakao.com/v1/user/access_token_info
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class KakaoTokenInfoResponse {

    private Long id;

    @JsonProperty("expires_in")
    private Integer expiresIn;

    @JsonProperty("app_id")
    private Long appId;
}
