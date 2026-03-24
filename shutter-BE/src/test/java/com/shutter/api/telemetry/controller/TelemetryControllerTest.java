package com.shutter.api.telemetry.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.shutter.api.telemetry.service.TelemetryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        value = TelemetryController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
class TelemetryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TelemetryService telemetryService;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    @Test
    @DisplayName("유효한 요청 → 200 OK, received=1 반환")
    void receive_validRequest_returns200() throws Exception {
        given(telemetryService.saveTelemetry(any())).willReturn(1);

        mockMvc.perform(post("/api/v1/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validBody())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.received").value(1));
    }

    @Test
    @DisplayName("timestamp 필드 누락 → 400 Bad Request")
    void receive_missingTimestamp_returns400() throws Exception {
        Map<String, Object> body = validBody();
        body.remove("timestamp");

        mockMvc.perform(post("/api/v1/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("model_type 허용값 외 입력 → 400 Bad Request")
    void receive_invalidModelType_returns400() throws Exception {
        Map<String, Object> body = validBody();
        body.put("model_type", "INVALID_TYPE");

        mockMvc.perform(post("/api/v1/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("inference_success 필드 누락 → 400 Bad Request")
    void receive_missingInferenceSuccess_returns400() throws Exception {
        Map<String, Object> body = validBody();
        body.remove("inference_success");

        mockMvc.perform(post("/api/v1/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("failure_type 허용값 외 입력 → 400 Bad Request")
    void receive_invalidFailureType_returns400() throws Exception {
        Map<String, Object> body = validBody();
        body.put("failure_type", "CRASH");

        mockMvc.perform(post("/api/v1/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    // ─────────────── helper ───────────────

    private Map<String, Object> validBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("model_type", "FACE");
        body.put("model_version", "v1.0");
        body.put("inference_success", true);
        body.put("inference_latency_ms", 100);
        body.put("timestamp", "2024-01-01T12:00:00");
        body.put("device_hash", "device-hash-001");
        body.put("session_id", "session-001");
        body.put("app_pss_memory", 512);
        body.put("thermal_throttling_status", 0);
        body.put("thread_cpu_time", 1000);
        body.put("battery_current_now", -500.0);
        return body;
    }
}