package com.shutter.api.gps.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GpsLocationResponse {

    @JsonProperty("ward_id")
    private Long wardId;

    private String name;

    private Double lat;

    private Double lng;

    @JsonProperty("updated_at")
    private String updatedAt;

    @JsonProperty("is_sharing")
    private Boolean isSharing;
}
