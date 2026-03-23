package com.shutter.api.notification.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * 타임라인 목록 응답 DTO (Wrapper)
 */
@Getter
@AllArgsConstructor
public class TimelineListResponse {
    private List<TimelineItemResponse> timeline;

    public static TimelineListResponse of(List<TimelineItemResponse> timeline) {
        return new TimelineListResponse(timeline);
    }
}
