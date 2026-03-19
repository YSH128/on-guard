package com.shutter.api.notification.controller;

import com.shutter.api.global.common.ApiResponse;
import com.shutter.api.notification.dto.request.AlertRequest;
import com.shutter.api.notification.dto.response.AlertLogResponse;
import com.shutter.api.notification.service.AlertService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 알림(Alert) 관련 API 컨트롤러
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/alerts")
public class AlertController {

    private final AlertService alertService;

    /**
     * 알림 로그 조회
     *
     * @param wardId 피보호자 ID
     * @return 알림 로그 목록 (최신순)
     */
    @GetMapping("/logs/{wardId}")
    public ResponseEntity<ApiResponse<List<AlertLogResponse>>> getAlertLogs(
            @AuthenticationPrincipal Long userId,
            @PathVariable("wardId") Long wardId) {
        
        List<AlertLogResponse> responses = alertService.getAlertLogs(wardId);
        return ResponseEntity.ok(ApiResponse.of("알림 로그 조회 성공", responses));
    }

    /**
     * 알림 전송 (피보호자 -> 보호자)
     *
     * @param guardianId 보호자 ID
     * @param userId 현재 로그인한 피보호자 ID (JWT 추출)
     * @param request 알림 요청 정보
     * @return 전송된 알림 상세 정보 (로그)
     */
    @PostMapping("/send/{guardianId}")
    public ResponseEntity<ApiResponse<AlertLogResponse>> sendAlert(
            @PathVariable("guardianId") Long guardianId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody AlertRequest request) {
        
        AlertLogResponse response = alertService.sendAlert(userId, guardianId, request);
        return ResponseEntity.ok(ApiResponse.of("알림 전송 성공", response));
    }
}
