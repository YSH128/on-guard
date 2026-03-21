package com.shutter.api.notification.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.util.List;

/**
 * 알림 로그 목록 응답 래퍼 DTO
 */
@Getter
@AllArgsConstructor
public class AlertLogsResponse {
    private List<AlertLogResponse> notifications;

    public static AlertLogsResponse from(List<AlertLogResponse> notifications) {
        return new AlertLogsResponse(notifications);
    }
}
