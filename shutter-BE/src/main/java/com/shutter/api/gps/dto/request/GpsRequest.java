package com.shutter.api.gps.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class GpsRequest {

    @NotNull(message = "위도(lat)는 필수 입력값입니다.")
    @JsonProperty("lat")
    private Double lat;

    @NotNull(message = "경도(lng)는 필수 입력값입니다.")
    @JsonProperty("lng")
    private Double lng;
}
