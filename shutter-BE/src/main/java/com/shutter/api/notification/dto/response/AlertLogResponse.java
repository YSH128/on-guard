package com.shutter.api.notification.dto.response;

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
public class AlertLogResponse {

    private Long id;
    private String wardName;
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
                .wardName(log.getWard().getName())
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
