package com.shutter.api.telemetry.repository;

import com.shutter.api.telemetry.domain.AiTelemetryLog;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AiTelemetryLogRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    private static final String INSERT_SQL = """
            INSERT INTO ai_telemetry_logs
                (user_id, model_version, model_type, inference_success, failure_type,
                 inference_latency_ms, risk_score, `timestamp`, device_hash, session_id, device_temperature)
            VALUES
                (:userId, :modelVersion, :modelType, :inferenceSuccess, :failureType,
                 :inferenceLatencyMs, :riskScore, :timestamp, :deviceHash, :sessionId, :deviceTemperature)
            """;

    public void save(AiTelemetryLog log) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", log.userId())
                .addValue("modelVersion", log.modelVersion())
                .addValue("modelType", log.modelType().name())
                .addValue("inferenceSuccess", log.inferenceSuccess())
                .addValue("failureType", log.failureType() != null ? log.failureType().name() : null)
                .addValue("inferenceLatencyMs", log.inferenceLatencyMs())
                .addValue("riskScore", log.riskScore())
                .addValue("timestamp", log.timestamp())
                .addValue("deviceHash", log.deviceHash())
                .addValue("sessionId", log.sessionId())
                .addValue("deviceTemperature", log.deviceTemperature());

        jdbcTemplate.update(INSERT_SQL, params);
    }
}