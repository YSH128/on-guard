package com.shutter.api.telemetry.service;

import com.shutter.api.telemetry.dto.request.TelemetryRequest;
import com.shutter.api.telemetry.repository.AiTelemetryLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TelemetryServiceTest {

    @InjectMocks
    private TelemetryService telemetryService;

    @Mock
    private AiTelemetryLogRepository telemetryLogRepository;

    // ─────────────── saveTelemetry() ───────────────

    @Test
    @DisplayName("유효한 요청은 버퍼에 적재되고 1을 반환한다")
    void saveTelemetry_validRequest_returnsOne() {
        int result = telemetryService.saveTelemetry(validRequest("2024-01-01T12:00:00"));
        assertThat(result).isEqualTo(1);
    }

    @Test
    @DisplayName("OffsetDateTime 포맷 타임스탬프(+09:00)를 파싱할 수 있다")
    void saveTelemetry_offsetDateTimeTimestamp_parsedSuccessfully() {
        int result = telemetryService.saveTelemetry(validRequest("2024-01-01T12:00:00+09:00"));
        assertThat(result).isEqualTo(1);
    }

    @Test
    @DisplayName("LocalDateTime 포맷 타임스탬프를 파싱할 수 있다")
    void saveTelemetry_localDateTimeTimestamp_parsedSuccessfully() {
        int result = telemetryService.saveTelemetry(validRequest("2024-01-01T12:00:00"));
        assertThat(result).isEqualTo(1);
    }

    @Test
    @DisplayName("잘못된 타임스탬프 포맷이면 IllegalArgumentException이 발생한다")
    void saveTelemetry_invalidTimestamp_throwsException() {
        assertThatThrownBy(() -> telemetryService.saveTelemetry(validRequest("2024/01/01 12:00:00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid timestamp format");
    }

    // ─────────────── flushBuffer() ───────────────

    @Test
    @DisplayName("버퍼에 데이터가 있으면 saveAll이 1회 호출된다")
    void flushBuffer_withData_callsSaveAll() {
        telemetryService.saveTelemetry(validRequest("2024-01-01T12:00:00"));

        telemetryService.flushBuffer();

        then(telemetryLogRepository).should(times(1)).saveAll(anyList());
    }

    @Test
    @DisplayName("버퍼가 비어있으면 saveAll이 호출되지 않는다")
    void flushBuffer_emptyBuffer_doesNotCallSaveAll() {
        telemetryService.flushBuffer();

        then(telemetryLogRepository).should(never()).saveAll(anyList());
    }

    @Test
    @DisplayName("배치 실패 시 레코드가 재큐되어 다음 flushBuffer에서 재시도된다")
    void flushBuffer_onFailure_requeuesBatch() {
        telemetryService.saveTelemetry(validRequest("2024-01-01T12:00:00"));
        doThrow(new RuntimeException("DB error")).when(telemetryLogRepository).saveAll(anyList());

        telemetryService.flushBuffer(); // 실패 → retryCount 0→1, 재큐

        reset(telemetryLogRepository); // 이후 성공하도록 리셋
        telemetryService.flushBuffer(); // 재시도 → 성공

        then(telemetryLogRepository).should(times(1)).saveAll(anyList());
    }

    @Test
    @DisplayName("MAX_RETRY(3)를 초과하면 레코드를 드랍하고 버퍼가 비워진다")
    void flushBuffer_exceedsMaxRetry_dropsRecord() {
        telemetryService.saveTelemetry(validRequest("2024-01-01T12:00:00"));
        doThrow(new RuntimeException("DB error")).when(telemetryLogRepository).saveAll(anyList());

        telemetryService.flushBuffer(); // retryCount 0→1
        telemetryService.flushBuffer(); // retryCount 1→2
        telemetryService.flushBuffer(); // retryCount 2→3
        telemetryService.flushBuffer(); // retryCount 3→4 → 드랍

        // 드랍 후 버퍼가 비어있으므로 saveAll 호출 없음
        reset(telemetryLogRepository);
        telemetryService.flushBuffer();

        then(telemetryLogRepository).should(never()).saveAll(anyList());
    }

    // ─────────────── helper ───────────────

    private TelemetryRequest validRequest(String timestamp) {
        TelemetryRequest req = new TelemetryRequest();
        try {
            setField(req, "modelType", "FACE");
            setField(req, "modelVersion", "v1.0");
            setField(req, "inferenceSuccess", true);
            setField(req, "inferenceLatencyMs", 100L);
            setField(req, "timestamp", timestamp);
            setField(req, "deviceHash", "device-hash-001");
            setField(req, "sessionId", "session-001");
            setField(req, "appPssMemory", 512);
            setField(req, "thermalThrottlingStatus", 0);
            setField(req, "threadCpuTime", 1000L);
            setField(req, "batteryCurrentNow", -500.0f);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return req;
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}