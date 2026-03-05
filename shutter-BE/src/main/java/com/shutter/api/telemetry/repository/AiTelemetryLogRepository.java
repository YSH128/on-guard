package com.shutter.api.telemetry.repository;

import com.shutter.api.telemetry.domain.AiTelemetryLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AiTelemetryLogRepository extends JpaRepository<AiTelemetryLog, Integer> {
}