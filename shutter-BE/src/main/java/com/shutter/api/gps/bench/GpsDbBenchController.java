package com.shutter.api.gps.bench;

import com.shutter.api.global.common.ApiResponse;
import com.shutter.api.gps.dto.request.GpsRequest;
import com.shutter.api.gps.dto.response.GpsLocationResponse;
import com.shutter.api.gps.dto.response.GpsResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 벤치마크 비교군 컨트롤러: MySQL 직접 쓰기/조회 엔드포인트
 * (bench 프로필에서만 활성화)
 */
@RestController
@Profile("bench")
@RequiredArgsConstructor
@RequestMapping("/bench/gps-db")
public class GpsDbBenchController {

    private final GpsDbBenchService gpsDbBenchService;

    @PostMapping
    public ResponseEntity<ApiResponse<GpsResponse>> saveLocationDb(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody GpsRequest request) {

        GpsResponse response = gpsDbBenchService.saveLocationDb(userId, request);
        return ResponseEntity.ok(ApiResponse.of("DB 위치 정보 저장 성공", response));
    }

    @GetMapping("/{wardId}")
    public ResponseEntity<ApiResponse<GpsLocationResponse>> getLocationDb(
            @AuthenticationPrincipal Long guardianId,
            @PathVariable("wardId") Long wardId) {

        GpsLocationResponse response = gpsDbBenchService.getLocationDb(guardianId, wardId);
        return ResponseEntity.ok(ApiResponse.of("DB 위치 정보 조회 성공", response));
    }
}
