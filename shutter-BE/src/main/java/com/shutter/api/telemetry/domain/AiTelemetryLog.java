package com.shutter.api.telemetry.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("ai_telemetry_logs")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiTelemetryLog {

    @Id
    private Long id;

    @Column("user_id")
    private Integer userId;

    @Column("model_version")
    private String modelVersion;

    @Column("model_type")
    private String modelType;

    @Column("inference_success")
    private Boolean inferenceSuccess;

    @Column("failure_type")
    private String failureType;

    @Column("inference_latency_ms")
    private Long inferenceLatencyMs;

    @Column("risk_score")
    private Double riskScore;

    @Column("timestamp")
    private LocalDateTime timestamp;

    @Column("device_hash")
    private String deviceHash;

    @Column("session_id")
    private String sessionId;

    @Column("device_temperature")
    private Float deviceTemperature;
}