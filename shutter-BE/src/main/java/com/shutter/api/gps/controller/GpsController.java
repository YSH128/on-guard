package com.shutter.api.gps.controller;

import com.shutter.api.global.common.ApiResponse;
import com.shutter.api.gps.dto.request.GpsRequest;
import com.shutter.api.gps.dto.request.GpsSharingToggleRequest;
import com.shutter.api.gps.dto.request.GpsSessionRequest;
import com.shutter.api.gps.dto.response.GpsLocationResponse;
import com.shutter.api.gps.dto.response.GpsResponse;
import com.shutter.api.gps.dto.response.GpsSharingToggleResponse;
import com.shutter.api.gps.dto.response.GpsSessionResponse;
import com.shutter.api.gps.service.GpsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/gps")
public class GpsController {

    private final GpsService gpsService;

    @PostMapping
    public ResponseEntity<ApiResponse<GpsResponse>> saveLocation(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody GpsRequest request) {
        
        GpsResponse response = gpsService.saveLocation(userId, request);
        return ResponseEntity.ok(ApiResponse.of("위치 정보 저장 성공", response));
    }

    @GetMapping("/{wardId}")
    public ResponseEntity<ApiResponse<GpsLocationResponse>> getLocation(
            @AuthenticationPrincipal Long guardianId,
            @PathVariable("wardId") Long wardId) {
        
        GpsLocationResponse response = gpsService.getLocation(guardianId, wardId);
        return ResponseEntity.ok(ApiResponse.of("위치 정보 조회 성공", response));
    }

    @PutMapping("/sharing")
    public ResponseEntity<ApiResponse<GpsSharingToggleResponse>> toggleSharing(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody GpsSharingToggleRequest request) {
        
        GpsSharingToggleResponse response = gpsService.toggleSharing(userId, request);
        return ResponseEntity.ok(ApiResponse.of("위치 공유 상태 변경 성공", response));
    }

    /**
     * POST /api/v1/gps/session
     * 통화 연결 시 양측 앱이 각자의 GPS를 1회 저장.
     * 딥페이크 감지 시 상대방 국가 조회에 사용된다.
     */
    @PostMapping("/session")
    public ResponseEntity<ApiResponse<Void>> saveSessionLocation(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody GpsSessionRequest request) {

        gpsService.saveSessionLocation(userId, request);
        return ResponseEntity.ok(ApiResponse.of("통화 세션 위치 저장 성공", null));
    }

    /**
     * GET /api/v1/gps/session/{channelName}?opponentKakaoId={id}
     * 딥페이크 감지 시 상대방의 저장된 GPS → 국가명 반환.
     */
    @GetMapping("/session/{channelName}")
    public ResponseEntity<ApiResponse<GpsSessionResponse>> getSessionOpponentCountry(
            @AuthenticationPrincipal Long userId,
            @PathVariable String channelName,
            @RequestParam("opponentKakaoId") Long opponentKakaoId) {

        GpsSessionResponse response = gpsService.getSessionOpponentCountry(channelName, opponentKakaoId);
        return ResponseEntity.ok(ApiResponse.of("통화 세션 위치 조회 성공", response));
    }
}
