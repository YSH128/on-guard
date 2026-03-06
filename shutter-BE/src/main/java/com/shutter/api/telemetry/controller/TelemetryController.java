package com.shutter.api.telemetry.controller;

import com.shutter.api.global.common.ApiResponse;
import com.shutter.api.telemetry.dto.request.TelemetryRequest;
import com.shutter.api.telemetry.service.TelemetryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/telemetry")
@RequiredArgsConstructor
public class TelemetryController {

    private final TelemetryService telemetryService;

    @PostMapping
    public ResponseEntity<ApiResponse<?>> receive(@Valid @RequestBody TelemetryRequest request) {
        int count = telemetryService.saveTelemetry(request);
        return ResponseEntity.ok(ApiResponse.of("telemetry received", Map.of("received", count)));
    }
}