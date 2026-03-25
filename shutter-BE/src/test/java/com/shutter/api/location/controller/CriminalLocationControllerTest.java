package com.shutter.api.location.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.shutter.api.global.exception.BusinessException;
import com.shutter.api.global.exception.ErrorCode;
import com.shutter.api.location.service.CriminalLocationService;
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

import java.util.Collections;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        value = CriminalLocationController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
class CriminalLocationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CriminalLocationService criminalLocationService;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    private final Long userId = 1L;

    // ─── criminal-request ───────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/location/criminal-request - 성공: 202 반환")
    void requestCriminalLocation_success() throws Exception {
        willDoNothing().given(criminalLocationService).requestCriminalLocation(any(), any());

        String body = objectMapper.writeValueAsString(Map.of("opponent_kakao_id", 987654321L));

        mockMvc.perform(post("/api/v1/location/criminal-request")
                        .with(authentication(new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("위치 요청을 전송했습니다."));
    }

    @Test
    @DisplayName("POST /api/v1/location/criminal-request - 실패: 상대방 없음 404")
    void requestCriminalLocation_opponentNotFound() throws Exception {
        willThrow(new BusinessException(ErrorCode.OPPONENT_NOT_FOUND))
                .given(criminalLocationService).requestCriminalLocation(any(), any());

        String body = objectMapper.writeValueAsString(Map.of("opponent_kakao_id", 999999999L));

        mockMvc.perform(post("/api/v1/location/criminal-request")
                        .with(authentication(new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("OPPONENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /api/v1/location/criminal-request - 실패: FCM 전송 실패 500")
    void requestCriminalLocation_fcmFailed() throws Exception {
        willThrow(new BusinessException(ErrorCode.FCM_SEND_FAILED))
                .given(criminalLocationService).requestCriminalLocation(any(), any());

        String body = objectMapper.writeValueAsString(Map.of("opponent_kakao_id", 987654321L));

        mockMvc.perform(post("/api/v1/location/criminal-request")
                        .with(authentication(new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("FCM_SEND_FAILED"));
    }

    // ─── criminal-report ────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/location/criminal-report - 성공: 200 반환")
    void reportCriminalLocation_success() throws Exception {
        willDoNothing().given(criminalLocationService).reportCriminalLocation(any(), any());

        Map<String, Object> body = Map.of(
                "requester_kakao_id", 123456789L,
                "lat", 37.123456,
                "lng", 127.123456
        );

        mockMvc.perform(post("/api/v1/location/criminal-report")
                        .with(authentication(new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("위치 정보를 전달했습니다."));
    }

    @Test
    @DisplayName("POST /api/v1/location/criminal-report - 실패: 요청자 없음 404")
    void reportCriminalLocation_requesterNotFound() throws Exception {
        willThrow(new BusinessException(ErrorCode.OPPONENT_NOT_FOUND))
                .given(criminalLocationService).reportCriminalLocation(any(), any());

        Map<String, Object> body = Map.of(
                "requester_kakao_id", 999999999L,
                "lat", 37.123456,
                "lng", 127.123456
        );

        mockMvc.perform(post("/api/v1/location/criminal-report")
                        .with(authentication(new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("OPPONENT_NOT_FOUND"));
    }
}
