package com.shutter.api.gps.controller;

import com.shutter.api.global.common.ApiResponse;
import com.shutter.api.gps.dto.request.GpsRequest;
import com.shutter.api.gps.dto.request.GpsSharingToggleRequest;
import com.shutter.api.gps.dto.response.GpsLocationResponse;
import com.shutter.api.gps.dto.response.GpsResponse;
import com.shutter.api.gps.dto.response.GpsSharingToggleResponse;
import com.shutter.api.gps.service.GpsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/gps")
public class GpsController {

    private final GpsService gpsService;

    @PostMapping
    public ApiResponse<GpsResponse> saveLocation(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody GpsRequest request) {
        
        GpsResponse response = gpsService.saveLocation(userId, request);
        return ApiResponse.of("위치 정보 저장 성공", response);
    }

    @GetMapping("/{wardId}")
    public ApiResponse<GpsLocationResponse> getLocation(
            @AuthenticationPrincipal Long guardianId,
            @PathVariable("wardId") Long wardId) {
        
        GpsLocationResponse response = gpsService.getLocation(guardianId, wardId);
        return ApiResponse.of("위치 정보 조회 성공", response);
    }

    @PutMapping("/sharing")
    public ApiResponse<GpsSharingToggleResponse> toggleSharing(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody GpsSharingToggleRequest request) {
        
        GpsSharingToggleResponse response = gpsService.toggleSharing(userId, request);
        return ApiResponse.of("위치 공유 상태 변경 성공", response);
    }
}
