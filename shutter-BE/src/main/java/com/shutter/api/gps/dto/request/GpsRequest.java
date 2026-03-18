package com.shutter.api.gps.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class GpsRequest {

    @NotNull(message = "위도(lat)는 필수 입력값입니다.")
    private Double lat;

    @NotNull(message = "경도(lng)는 필수 입력값입니다.")
    private Double lng;
}
