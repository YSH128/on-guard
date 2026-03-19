package com.shutter.api.gps.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class GpsSharingToggleResponse {

    @JsonProperty("gps_sharing")
    private Boolean gpsSharing;
}
