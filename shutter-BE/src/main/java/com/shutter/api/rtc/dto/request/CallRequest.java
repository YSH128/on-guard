package com.shutter.api.rtc.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CallRequest {
    
    @NotNull(message = "수신자 ID는 필수입니다.")
    @JsonProperty("callee_id")
    private Long calleeId;
}
