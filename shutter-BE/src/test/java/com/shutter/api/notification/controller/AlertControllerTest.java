package com.shutter.api.notification.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.shutter.api.global.exception.AlertException;
import com.shutter.api.global.exception.ErrorCode;
import com.shutter.api.notification.domain.NotificationEventType;
import com.shutter.api.notification.domain.NotificationStatus;
import com.shutter.api.notification.dto.request.AlertRequest;
import com.shutter.api.notification.dto.response.AlertLogResponse;
import com.shutter.api.notification.service.AlertService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        value = AlertController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
class AlertControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AlertService alertService;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    private Long userId;

    @BeforeEach
    void setUp() {
        userId = 1L;
    }

    @Test
    @DisplayName("GET /api/v1/alerts/logs/{wardId} - 성공: 로그 목록 반환")
    void getAlertLogs_success() throws Exception {
        // given
        AlertLogResponse logResponse = AlertLogResponse.builder()
                .id(1L)
                .wardName("피보호자")
                .guardianName("보호자")
                .eventType("DEEPFAKE")
                .status(NotificationStatus.SUCCESS)
                .sentAt(LocalDateTime.now())
                .build();

        given(alertService.getAlertLogs(anyLong())).willReturn(List.of(logResponse));

        // when & then
        mockMvc.perform(get("/api/v1/alerts/logs/1")
                        .with(authentication(new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(1L))
                .andExpect(jsonPath("$.data[0].ward_name").value("피보호자"));
    }

    @Test
    @DisplayName("GET /api/v1/alerts/logs/{wardId} - 실패: 존재하지 않는 wardId")
    void getAlertLogs_fail_wardNotFound() throws Exception {
        // given
        given(alertService.getAlertLogs(anyLong()))
                .willThrow(new AlertException(ErrorCode.WARD_NOT_FOUND));

        // when & then
        mockMvc.perform(get("/api/v1/alerts/logs/999")
                        .with(authentication(new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList()))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WARD_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /api/v1/alerts/send/{guardianId} - 성공: 알림 전송 및 로그 반환")
    void sendAlert_success() throws Exception {
        // given
        AlertRequest request = new AlertRequest("010-1234-5678", NotificationEventType.DEEPFAKE, LocalDateTime.now());
        AlertLogResponse logResponse = AlertLogResponse.builder()
                .id(1L)
                .status(NotificationStatus.SUCCESS)
                .build();

        given(alertService.sendAlert(any(), any(), any())).willReturn(logResponse);

        // when & then
        mockMvc.perform(post("/api/v1/alerts/send/2")
                        .with(authentication(new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"));
    }

    @Test
    @DisplayName("POST /api/v1/alerts/send/{guardianId} - 실패: 존재하지 않는 guardianId")
    void sendAlert_fail_guardianNotFound() throws Exception {
        // given
        AlertRequest request = new AlertRequest("caller", NotificationEventType.DEEPFAKE, null);
        given(alertService.sendAlert(any(), any(), any()))
                .willThrow(new AlertException(ErrorCode.GUARDIAN_NOT_FOUND));

        // when & then
        mockMvc.perform(post("/api/v1/alerts/send/999")
                        .with(authentication(new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GUARDIAN_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /api/v1/alerts/send/{guardianId} - 실패: 관계 없음")
    void sendAlert_fail_relationshipNotFound() throws Exception {
        // given
        AlertRequest request = new AlertRequest("caller", NotificationEventType.DEEPFAKE, null);
        given(alertService.sendAlert(any(), any(), any()))
                .willThrow(new AlertException(ErrorCode.RELATIONSHIP_NOT_FOUND));

        // when & then
        mockMvc.perform(post("/api/v1/alerts/send/3")
                        .with(authentication(new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("RELATIONSHIP_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /api/v1/alerts/send/{guardianId} - 실패: FCM 발송 실패")
    void sendAlert_fail_fcmSendFailed() throws Exception {
        // given
        AlertRequest request = new AlertRequest("caller", NotificationEventType.DEEPFAKE, null);
        given(alertService.sendAlert(any(), any(), any()))
                .willThrow(new AlertException(ErrorCode.FCM_SEND_FAILED));

        // when & then
        mockMvc.perform(post("/api/v1/alerts/send/2")
                        .with(authentication(new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("FCM_SEND_FAILED"));
    }
}
