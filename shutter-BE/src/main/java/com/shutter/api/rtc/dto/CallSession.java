package com.shutter.api.rtc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CallSession {

    @JsonProperty("caller_id")
    private Long callerId;

    @JsonProperty("callee_id")
    private Long calleeId;
}
