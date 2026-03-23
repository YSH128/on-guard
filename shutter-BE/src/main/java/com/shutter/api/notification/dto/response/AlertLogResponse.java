package com.shutter.api.notification.dto.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.shutter.api.notification.domain.NotificationLog;
import com.shutter.api.notification.domain.NotificationStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 알림 로그 응답 DTO
 */
@Getter
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AlertLogResponse {

    private Long id;
    private Long wardId;
    private Long guardianId;
    private String guardianName;
    private String eventType;
    private String caller;
    private LocalDateTime callStartedAt;
    private NotificationStatus status;
    private String failReason;
    private LocalDateTime sentAt;

    public static AlertLogResponse from(NotificationLog log) {
        return AlertLogResponse.builder()
                .id(log.getId())
                .wardId(log.getWard().getId())
                .guardianId(log.getGuardian().getId())
                .guardianName(log.getGuardian().getName())
                .eventType(log.getEventType().name())
                .caller(log.getCaller())
                .callStartedAt(log.getCallStartedAt())
                .status(log.getStatus())
                .failReason(log.getFailReason())
                .sentAt(log.getSentAt())
                .build();
    }
}
