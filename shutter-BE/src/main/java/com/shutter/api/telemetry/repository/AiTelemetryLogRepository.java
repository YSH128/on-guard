package com.shutter.api.telemetry.repository;

import com.shutter.api.telemetry.domain.AiTelemetryLog;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AiTelemetryLogRepository extends ReactiveCrudRepository<AiTelemetryLog, Long> {
}