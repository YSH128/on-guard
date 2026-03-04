package com.shutter.api.telemetry.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.shutter.api.telemetry.domain.FailureType;
import com.shutter.api.telemetry.domain.ModelType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TelemetryRequest(

        @JsonProperty("model_type")
        @NotNull(message = "model_type is required")
        ModelType modelType,

        @JsonProperty("model_version")
        @NotBlank(message = "model_version is required")
        String modelVersion,

        @JsonProperty("inference_success")
        @NotNull(message = "inference_success is required")
        Boolean inferenceSuccess,

        @JsonProperty("failure_type")
        FailureType failureType,

        @JsonProperty("inference_latency_ms")
        @NotNull(message = "inference_latency_ms is required")
        Long inferenceLatencyMs,

        @JsonProperty("risk_score")
        Double riskScore,

        @JsonProperty("timestamp")
        @NotBlank(message = "timestamp is required")
        String timestamp,

        @JsonProperty("device_hash")
        @NotBlank(message = "device_hash is required")
        String deviceHash,

        @JsonProperty("session_id")
        @NotBlank(message = "session_id is required")
        String sessionId,

        @JsonProperty("device_temperature")
        Float deviceTemperature
) {
}