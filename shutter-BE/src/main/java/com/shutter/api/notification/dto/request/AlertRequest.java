package com.shutter.api.notification.dto.request;

import com.shutter.api.notification.domain.NotificationEventType;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 알림 전송 요청 DTO
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AlertRequest {

    private String caller;

    @com.fasterxml.jackson.annotation.JsonProperty("event_type")
    @NotNull(message = "이벤트 타입은 필수입니다.")
    private NotificationEventType eventType;

    @com.fasterxml.jackson.annotation.JsonProperty("phishing_type")
    private String phishingType;

    @com.fasterxml.jackson.annotation.JsonProperty("call_started_at")
    private LocalDateTime callStartedAt;

    public AlertRequest(String caller, NotificationEventType eventType, String phishingType, LocalDateTime callStartedAt) {
        this.caller = caller;
        this.eventType = eventType;
        this.phishingType = phishingType;
        this.callStartedAt = callStartedAt;
    }
}
