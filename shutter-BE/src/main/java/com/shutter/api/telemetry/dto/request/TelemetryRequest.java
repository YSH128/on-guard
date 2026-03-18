package com.shutter.api.telemetry.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class TelemetryRequest {

    @NotBlank
    @Pattern(regexp = "FACE|LIP|OCR", message = "model_type must be FACE, LIP, or OCR")
    private String modelType;

    @NotBlank
    private String modelVersion;

    @NotNull
    private Boolean inferenceSuccess;

    @Pattern(
            regexp = "^$|MODEL_LOAD_FAIL|TIMEOUT|INFERENCE_ERROR|UNKNOWN",
            message = "failure_type must be one of MODEL_LOAD_FAIL, TIMEOUT, INFERENCE_ERROR, UNKNOWN"
    )
    private String failureType;

    @NotNull
    private Long inferenceLatencyMs;

    private Double riskScore;

    @NotBlank
    private String timestamp;

    @NotBlank
    private String deviceHash;

    @NotBlank
    private String sessionId;

    private Float deviceTemperature;

    private Long userId;

    @NotNull
    private Integer appPssMemory;

    @NotNull
    private Integer thermalThrottlingStatus;

    @NotNull
    private Long threadCpuTime;

    @NotNull
    private Float batteryCurrentNow;

    private Integer modelLoadLatencyMs;
}