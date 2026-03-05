package com.shutter.api.telemetry.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "ai_telemetry_logs")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiTelemetryLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "model_version", nullable = false)
    private String modelVersion;

    @Column(name = "model_type", nullable = false)
    private String modelType;

    @Column(name = "inference_success", nullable = false)
    private Boolean inferenceSuccess;

    @Column(name = "failure_type")
    private String failureType;

    @Column(name = "inference_latency_ms", nullable = false)
    private Long inferenceLatencyMs;

    @Column(name = "risk_score")
    private Double riskScore;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "device_hash", nullable = false)
    private String deviceHash;

    @Column(name = "session_id", nullable = false)
    private String sessionId;

    @Column(name = "device_temperature", nullable = false)
    private Float deviceTemperature;
}