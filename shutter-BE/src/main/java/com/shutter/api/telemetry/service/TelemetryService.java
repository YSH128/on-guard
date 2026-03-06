package com.shutter.api.telemetry.service;

import com.shutter.api.telemetry.domain.AiTelemetryLog;
import com.shutter.api.telemetry.dto.request.TelemetryRequest;
import com.shutter.api.telemetry.repository.AiTelemetryLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelemetryService {

    private static final int BATCH_SIZE = 100;

    private final AiTelemetryLogRepository telemetryLogRepository;
    private final Queue<AiTelemetryLog> buffer = new ConcurrentLinkedQueue<>();

    public int saveTelemetry(TelemetryRequest request) {
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
                .appPssMemory(request.getAppPssMemory())
                .thermalThrottlingStatus(request.getThermalThrottlingStatus())
                .threadCpuTime(request.getThreadCpuTime())
                .batteryCurrentNow(request.getBatteryCurrentNow())
                .modelLoadLatencyMs(request.getModelLoadLatencyMs())
                .build();

        buffer.offer(telemetryLog);
        return 1;
    }

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void flushBuffer() {
        List<AiTelemetryLog> batch = new ArrayList<>();
        AiTelemetryLog item;
        int count = 0;
        while ((item = buffer.poll()) != null && count++ < BATCH_SIZE) {
            batch.add(item);
        }
        if (batch.isEmpty()) return;

        try {
            telemetryLogRepository.saveAll(batch);
            log.debug("Batch inserted {} telemetry records", batch.size());
        } catch (Exception e) {
            log.error("Batch insert failed ({} records), re-queuing for retry: {}", batch.size(), e.getMessage());
            batch.forEach(buffer::offer);
        }
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
}