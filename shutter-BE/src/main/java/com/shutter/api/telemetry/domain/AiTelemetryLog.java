package com.shutter.api.telemetry.domain;

import java.time.LocalDateTime;

public record AiTelemetryLog(
        Integer userId,
        String modelVersion,
        ModelType modelType,
        boolean inferenceSuccess,
        FailureType failureType,
        long inferenceLatencyMs,
        Double riskScore,
        LocalDateTime timestamp,
        String deviceHash,
        String sessionId,
        float deviceTemperature
) {
}