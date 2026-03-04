package com.shutter.api.telemetry.service;

import com.shutter.api.telemetry.domain.AiTelemetryLog;
import com.shutter.api.telemetry.dto.request.TelemetryRequest;
import com.shutter.api.telemetry.repository.AiTelemetryLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

@Slf4j
@Service
public class TelemetryService implements DisposableBean {

    private static final int BATCH_SIZE = 100;
    private static final Duration BATCH_TIMEOUT = Duration.ofSeconds(1);

    private final AiTelemetryLogRepository telemetryLogRepository;
    private final Sinks.Many<AiTelemetryLog> eventSink;
    private final Disposable subscription;

    public TelemetryService(AiTelemetryLogRepository telemetryLogRepository) {
        this.telemetryLogRepository = telemetryLogRepository;
        this.eventSink = Sinks.many().multicast().onBackpressureBuffer(10_000);

        this.subscription = eventSink.asFlux()
                .bufferTimeout(BATCH_SIZE, BATCH_TIMEOUT)
                .flatMap(telemetryLogRepository::saveAll)
                .subscribe(
                        saved -> {},
                        error -> log.error("Batch insert failed", error)
                );
    }

    public Mono<Integer> saveTelemetry(TelemetryRequest request) {
        LocalDateTime timestamp = parseTimestamp(request.getTimestamp());

        AiTelemetryLog telemetryLog = AiTelemetryLog.builder()
                .modelType(request.getModelType())
                .modelVersion(request.getModelVersion())
                .inferenceSuccess(request.getInferenceSuccess())
                .failureType(request.getFailureType())
                .inferenceLatencyMs(request.getInferenceLatencyMs())
                .riskScore(request.getRiskScore())
                .timestamp(timestamp)
                .deviceHash(request.getDeviceHash())
                .sessionId(request.getSessionId())
                .deviceTemperature(request.getDeviceTemperature() != null ? request.getDeviceTemperature() : 0.0f)
                .build();

        Sinks.EmitResult result = eventSink.tryEmitNext(telemetryLog);
        if (result.isFailure()) {
            log.warn("Event buffer overflow, telemetry event dropped: {}", result);
        }

        return Mono.just(1);
    }

    private LocalDateTime parseTimestamp(String timestamp) {
        try {
            return OffsetDateTime.parse(timestamp).toLocalDateTime();
        } catch (DateTimeParseException e) {
            try {
                return LocalDateTime.parse(timestamp);
            } catch (DateTimeParseException e2) {
                throw new IllegalArgumentException("Invalid timestamp format: " + timestamp);
            }
        }
    }

    @Override
    public void destroy() {
        subscription.dispose();
    }
}