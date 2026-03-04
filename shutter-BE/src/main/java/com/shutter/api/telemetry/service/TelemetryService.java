package com.shutter.api.telemetry.service;

import com.shutter.api.telemetry.domain.AiTelemetryLog;
import com.shutter.api.telemetry.dto.request.TelemetryRequest;
import com.shutter.api.telemetry.dto.response.TelemetryResponse;
import com.shutter.api.telemetry.repository.AiTelemetryLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class TelemetryService {

    private final AiTelemetryLogRepository telemetryLogRepository;

    public Mono<TelemetryResponse> save(TelemetryRequest request) {
        return Mono.fromCallable(() -> {
            AiTelemetryLog log = new AiTelemetryLog(
                    null,
                    request.modelVersion(),
                    request.modelType(),
                    request.inferenceSuccess(),
                    request.failureType(),
                    request.inferenceLatencyMs(),
                    request.riskScore(),
                    OffsetDateTime.parse(request.timestamp()).toLocalDateTime(),
                    request.deviceHash(),
                    request.sessionId(),
                    request.deviceTemperature() != null ? request.deviceTemperature() : 0.0f
            );
            telemetryLogRepository.save(log);
            return new TelemetryResponse(1);
        }).subscribeOn(Schedulers.boundedElastic());
    }
}