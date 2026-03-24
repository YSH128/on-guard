package com.shutter.api.location.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CriminalLocationReportDto {

    @NotNull
    private Long requesterKakaoId;

    @NotNull
    private Double lat;

    @NotNull
    private Double lng;
}