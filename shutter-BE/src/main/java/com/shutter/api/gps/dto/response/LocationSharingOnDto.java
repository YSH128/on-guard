package com.shutter.api.gps.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LocationSharingOnDto {

    private final String type = "LOCATION_SHARING_ON";

    @JsonProperty("ward_id")
    private Long wardId;

    @Builder.Default
    @JsonProperty("is_sharing")
    private Boolean isSharing = true;
}
