package com.shutter.api.telemetry.controller;

import com.shutter.api.global.common.ApiResponse;
import com.shutter.api.telemetry.dto.request.TelemetryRequest;
import com.shutter.api.telemetry.dto.response.TelemetryResponse;
import com.shutter.api.telemetry.service.TelemetryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
public class TelemetryController {

    private final TelemetryService telemetryService;

    @PostMapping("/telemetry")
    public Mono<ResponseEntity<ApiResponse<TelemetryResponse>>> receiveTelemetry(
            @Valid @RequestBody TelemetryRequest request) {
        return telemetryService.save(request)
                .map(response -> ResponseEntity.ok(ApiResponse.of("telemetry received", response)));
    }
}
