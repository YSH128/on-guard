package com.shutter.api.gps.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LocationUpdateDto {

    private final String type = "LOCATION_UPDATE";
    private Long wardId;
    private String name;
    private Double lat;
    private Double lng;
    private String updatedAt;
    private Boolean isSharing;
}
