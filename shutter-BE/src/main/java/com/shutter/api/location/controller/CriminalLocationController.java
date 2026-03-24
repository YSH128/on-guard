package com.shutter.api.location.controller;

import com.shutter.api.global.common.ApiResponse;
import com.shutter.api.location.dto.request.CriminalLocationReportDto;
import com.shutter.api.location.dto.request.CriminalLocationRequestDto;
import com.shutter.api.location.service.CriminalLocationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/location")
public class CriminalLocationController {

    private final CriminalLocationService criminalLocationService;

    /**
     * POST /api/v1/location/criminal-request
     * 딥페이크/딥보이스 감지 시 상대방 위치 요청
     */
    @PostMapping("/criminal-request")
    public ResponseEntity<ApiResponse<Void>> requestCriminalLocation(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody CriminalLocationRequestDto dto) {

        criminalLocationService.requestCriminalLocation(userId, dto);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new ApiResponse<>(true, "위치 요청을 전송했습니다.", null));
    }

    /**
     * POST /api/v1/location/criminal-report
     * 상대방 앱이 GPS 위치를 서버로 보고
     */
    @PostMapping("/criminal-report")
    public ResponseEntity<ApiResponse<Void>> reportCriminalLocation(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody CriminalLocationReportDto dto) {

        criminalLocationService.reportCriminalLocation(userId, dto);
        return ResponseEntity.ok(new ApiResponse<>(true, "위치 정보를 전달했습니다.", null));
    }
}