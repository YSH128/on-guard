package com.shutter.api.gps.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class GpsResponse {
    
    // yyyy-MM-dd'T'HH:mm:ss 포맷으로 자동 직렬화됨 (application.yaml의 write-dates-as-timestamps: false 설정)
    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;
}
