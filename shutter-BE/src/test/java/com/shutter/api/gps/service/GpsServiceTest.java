package com.shutter.api.gps.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shutter.api.gps.dto.request.GpsRequest;
import com.shutter.api.gps.dto.response.GpsResponse;
import com.shutter.api.notification.client.KakaoPushClient;
import com.shutter.api.notification.domain.NotificationEventType;
import com.shutter.api.notification.domain.PushResult;
import com.shutter.api.user.domain.User;
import com.shutter.api.user.domain.UserRelationship;
import com.shutter.api.user.repository.UserRelationshipRepository;
import com.shutter.api.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GpsServiceTest {

    @InjectMocks
    private GpsService gpsService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserRelationshipRepository userRelationshipRepository;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private KakaoPushClient kakaoPushClient;

    private User ward;
    private User guardian;
    private UserRelationship relationship;
    private GpsRequest gpsRequest;

    @BeforeEach
    void setUp() throws Exception {
        ward = createUser(1L, 111L, "홍길동");
        guardian = createUser(2L, 222L, "보호자");

        relationship = mock(UserRelationship.class);

        gpsRequest = new GpsRequest();
        ReflectionTestUtils.setField(gpsRequest, "lat", 37.5);
        ReflectionTestUtils.setField(gpsRequest, "lng", 127.0);

        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(objectMapper.writeValueAsString(any())).willReturn("{\"lat\":37.5,\"lng\":127.0}");
    }

    @Test
    @DisplayName("최초 GPS 전송 - 보호자에게 FCM 발송")
    void saveLocation_firstGps_sendsFcmToGuardians() {
        given(relationship.getGuardian()).willReturn(guardian);
        given(userRepository.findById(1L)).willReturn(Optional.of(ward));
        given(valueOperations.setIfAbsent(eq("location:1"), anyString(), anyLong(), any())).willReturn(true); // 최초
        given(userRelationshipRepository.findByWardIdWithGuardian(1L)).willReturn(List.of(relationship));
        given(kakaoPushClient.sendPush(anyLong(), anyString(), eq(NotificationEventType.LOCATION), anyString()))
                .willReturn(PushResult.SUCCESS);

        GpsResponse response = gpsService.saveLocation(1L, gpsRequest);

        assertThat(response).isNotNull();
        verify(kakaoPushClient, times(1)).sendPush(
                eq(222L), eq("홍길동"), eq(NotificationEventType.LOCATION), anyString());
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/location/1"), any(Object.class));
    }

    @Test
    @DisplayName("이후 GPS 전송 - FCM 재발송 안함")
    void saveLocation_subsequentGps_doesNotSendFcm() {
        given(userRepository.findById(1L)).willReturn(Optional.of(ward));
        given(valueOperations.setIfAbsent(eq("location:1"), anyString(), anyLong(), any())).willReturn(false); // 이미 키 있음
        given(userRelationshipRepository.findByWardIdWithGuardian(1L)).willReturn(List.of(relationship));

        gpsService.saveLocation(1L, gpsRequest);

        verify(kakaoPushClient, never()).sendPush(anyLong(), anyString(), any(), anyString());
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/location/1"), any(Object.class));
    }

    @Test
    @DisplayName("보호자 없는 경우 - FCM 및 WebSocket 미발송")
    void saveLocation_noGuardians_doesNotSendAnything() {
        given(userRepository.findById(1L)).willReturn(Optional.of(ward));
        given(valueOperations.setIfAbsent(eq("location:1"), anyString(), anyLong(), any())).willReturn(true);
        given(userRelationshipRepository.findByWardIdWithGuardian(1L)).willReturn(List.of());

        gpsService.saveLocation(1L, gpsRequest);

        verify(kakaoPushClient, never()).sendPush(anyLong(), anyString(), any(), anyString());
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    private User createUser(Long id, Long kakaoId, String name) {
        User user = User.builder()
                .kakaoId(kakaoId)
                .name(name)
                .deviceUuid("uuid-" + id)
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}