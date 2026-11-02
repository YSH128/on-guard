package com.shutter.api.rtc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CallSession {

    public enum CallStatus {
        RINGING,
        CONNECTED,
        ENDED
    }

    @JsonProperty("caller_id")
    private Long callerId;

    @JsonProperty("callee_id")
    private Long calleeId;

    @JsonProperty("status")
    @Builder.Default
    private CallStatus status = CallStatus.RINGING;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    public void updateStatus(CallStatus status) {
        this.status = status;
    }
}
