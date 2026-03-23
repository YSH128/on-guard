package com.shutter.api.notification.controller;

import com.shutter.api.notification.dto.response.TimelineItemResponse;
import com.shutter.api.notification.dto.response.TimelineListResponse;
import com.shutter.api.notification.service.AlertService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        value = TimelineController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
class TimelineControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AlertService alertService;

    @Test
    @DisplayName("GET /api/v1/timeline/{wardId} - 성공: 타임라인 목록 반환")
    void getTimeline_success() throws Exception {
        // given
        TimelineItemResponse item = TimelineItemResponse.builder()
                .type("MESSAGE")
                .timestamp(LocalDateTime.of(2026, 3, 16, 14, 34, 0))
                .tag("자동 발송")
                .toName("김철수")
                .status("SUCCESS")
                .callStartedAt(LocalDateTime.of(2026, 3, 16, 14, 32, 0))
                .caller("알 수 없음 (발신자 제한 번호)")
                .build();

        TimelineListResponse response = TimelineListResponse.of(List.of(item));

        given(alertService.getTimeline(anyLong())).willReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/timeline/1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("타임라인 조회 성공"))
                .andExpect(jsonPath("$.data.timeline[0].type").value("MESSAGE"))
                .andExpect(jsonPath("$.data.timeline[0].tag").value("자동 발송"))
                .andExpect(jsonPath("$.data.timeline[0].to_name").value("김철수"));
    }
}
