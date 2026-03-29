package com.shutter.api.gps.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 통화 세션 GPS 저장 요청 DTO
 * 통화 연결 시 양측 앱이 각자의 현재 위치를 1회 전송한다.
 */
@Getter
@NoArgsConstructor
public class GpsSessionRequest {

    @NotBlank(message = "채널명은 필수 입력값입니다.")
    @JsonProperty("channel_name")
    private String channelName;

    @NotNull(message = "위도(lat)는 필수 입력값입니다.")
    @JsonProperty("lat")
    private Double lat;

    @NotNull(message = "경도(lng)는 필수 입력값입니다.")
    @JsonProperty("lng")
    private Double lng;
}
