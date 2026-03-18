package com.shutter.api.gps.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LocationExpiredDto {

    private final String type = "LOCATION_EXPIRED";
    private Long wardId;
    @Builder.Default
    private Boolean isSharing = false;
}
