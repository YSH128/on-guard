package com.shutter.api.gps.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class GpsSharingToggleRequest {

    @NotNull(message = "gps 공유 상태는 필수 입력값입니다.")
    private Boolean gpsSharing;
}
