package com.shutter.api.notification.dto.response;

import com.shutter.api.notification.domain.NotificationLog;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 타임라인 개별 항목 응답 DTO
 */
@Getter
@Builder
public class TimelineItemResponse {
    private String type;           // MESSAGE, LOCATION
    private LocalDateTime timestamp;  // 발송 시각
    private String tag;            // 자동 발송, 위치 공유
    private String toName;         // 수신 보호자 이름
    private String status;         // SUCCESS, FAIL
    private LocalDateTime callStartedAt; // 통화 시작 시각
    private String caller;         // 발신자 정보 (딥페이크용)

    public static TimelineItemResponse from(NotificationLog log) {
        return TimelineItemResponse.builder()
                .type(log.getEventType().getTimelineType())
                .timestamp(log.getSentAt())
                .tag(log.getEventType().getTimelineTag())
                .toName(log.getGuardian().getName())
                .status(log.getStatus().name())
                .callStartedAt(log.getCallStartedAt())
                .caller(log.getCaller())
                .build();
    }
}
