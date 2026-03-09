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
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelemetryService {

    private static final int BATCH_SIZE = 100;
    private static final int MAX_RETRY = 3;

    /**
     * ISO_OFFSET_DATE_TIME (e.g. 2024-01-01T12:00:00+09:00) 와
     * ISO_LOCAL_DATE_TIME  (e.g. 2024-01-01T12:00:00)         둘 다 허용.
     */
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = new DateTimeFormatterBuilder()
            .append(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            .optionalStart()
            .appendOffsetId()
            .optionalEnd()
            .toFormatter();

    private final AiTelemetryLogRepository telemetryLogRepository;
    private final Queue<RetryableItem> buffer = new ConcurrentLinkedQueue<>();

    private record RetryableItem(AiTelemetryLog log, int retryCount) {
        RetryableItem(AiTelemetryLog log) {
            this(log, 0);
        }
        RetryableItem withRetry() {
            return new RetryableItem(log, retryCount + 1);
        }
    }

    public int saveTelemetry(TelemetryRequest request) {
        LocalDateTime timestamp = parseTimestamp(request.getTimestamp());

        AiTelemetryLog telemetryLog = AiTelemetryLog.builder()
                .userId(request.getUserId())
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

        buffer.offer(new RetryableItem(telemetryLog));
        return 1;
    }

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void flushBuffer() {
        List<RetryableItem> batch = new ArrayList<>();
        RetryableItem item;
        int count = 0;
        while ((item = buffer.poll()) != null && count++ < BATCH_SIZE) {
            batch.add(item);
        }
        if (batch.isEmpty()) return;

        try {
            telemetryLogRepository.saveAll(batch.stream().map(RetryableItem::log).toList());
            log.debug("Batch inserted {} telemetry records", batch.size());
        } catch (Exception e) {
            log.error("Batch insert failed ({} records): {}", batch.size(), e.getMessage());
            batch.stream()
                    .map(RetryableItem::withRetry)
                    .filter(retried -> {
                        if (retried.retryCount() > MAX_RETRY) {
                            log.error("Dropping telemetry record after {} retries: userId={}, timestamp={}",
                                    MAX_RETRY, retried.log().getUserId(), retried.log().getTimestamp());
                            return false;
                        }
                        return true;
                    })
                    .forEach(buffer::offer);
        }
    }

    private LocalDateTime parseTimestamp(String timestamp) {
        try {
            TemporalAccessor parsed = TIMESTAMP_FORMATTER.parseBest(
                    timestamp, OffsetDateTime::from, LocalDateTime::from);
            if (parsed instanceof OffsetDateTime odt) {
                return odt.toLocalDateTime();
            }
            return (LocalDateTime) parsed;
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "Invalid timestamp format: " + timestamp
                    + ". Accepted formats: ISO_OFFSET_DATE_TIME (2024-01-01T12:00:00+09:00)"
                    + " or ISO_LOCAL_DATE_TIME (2024-01-01T12:00:00)");
        }
    }
}