package com.shutter.api.notification.controller;

import com.shutter.api.global.common.ApiResponse;
import com.shutter.api.notification.dto.response.TimelineListResponse;
import com.shutter.api.notification.service.AlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 타임라인(Timeline) 관련 API 컨트롤러
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/timeline")
public class TimelineController {

    private final AlertService alertService;

    /**
     * 알림 타임라인 확인
     *
     * @param wardId 피보호자 ID
     * @return 타임라인 데이터 목록
     */
    @GetMapping("/{wardId}")
    public ResponseEntity<ApiResponse<TimelineListResponse>> getTimeline(
            @PathVariable("wardId") Long wardId) {
        
        TimelineListResponse response = alertService.getTimeline(wardId);
        return ResponseEntity.ok(ApiResponse.of("타임라인 조회 성공", response));
    }
}
