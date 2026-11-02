package com.shutter.api.gps.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shutter.api.global.exception.BusinessException;
import com.shutter.api.global.exception.ErrorCode;
import com.shutter.api.gps.dto.request.GpsRequest;
import com.shutter.api.gps.dto.response.GpsResponse;
import com.shutter.api.notification.client.KakaoPushClient;
import com.shutter.api.notification.domain.NotificationEventType;
import com.shutter.api.notification.domain.PushResult;
import com.shutter.api.user.domain.Role;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
        ward.assignRole(Role.WARD);
        ward.updateGpsSharing(true);

        guardian = createUser(2L, 222L, "보호자");
        guardian.assignRole(Role.GUARDIAN);

        relationship = mock(UserRelationship.class);

        gpsRequest = new GpsRequest(37.5, 127.0);

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{\"lat\":37.5,\"lng\":127.0}");
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

    @Test
    @DisplayName("인가 검증 - 피보호자(WARD)가 아닌 사용자가 GPS 전송 시 UNAUTHORIZED_ACCESS 발생")
    void saveLocation_unauthorizedRole_throwsUnauthorizedAccess() {
        ward.assignRole(Role.GUARDIAN); // 보호자가 전송 시도
        given(userRepository.findById(1L)).willReturn(Optional.of(ward));

        assertThatThrownBy(() -> gpsService.saveLocation(1L, gpsRequest))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHORIZED_ACCESS);

        verify(valueOperations, never()).setIfAbsent(anyString(), anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("설정 검증 - 위치 공유가 비활성화(OFF)된 경우 GPS_SHARING_DISABLED 발생 및 저장 차단")
    void saveLocation_sharingDisabled_throwsGpsSharingDisabled() {
        ward.updateGpsSharing(false); // 위치 공유 OFF 상태
        given(userRepository.findById(1L)).willReturn(Optional.of(ward));

        assertThatThrownBy(() -> gpsService.saveLocation(1L, gpsRequest))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GPS_SHARING_DISABLED);

        verify(valueOperations, never()).setIfAbsent(anyString(), anyString(), anyLong(), any());
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    @DisplayName("장애 격리 - 외부 카카오 푸시 전송 실패 시에도 위치 저장 및 웹소켓 브로드캐스팅은 정상 성공")
    void saveLocation_pushFails_stillSavesLocationAndPublishesWebSocket() {
        given(relationship.getGuardian()).willReturn(guardian);
        given(userRepository.findById(1L)).willReturn(Optional.of(ward));
        given(valueOperations.setIfAbsent(eq("location:1"), anyString(), anyLong(), any())).willReturn(true);
        given(userRelationshipRepository.findByWardIdWithGuardian(1L)).willReturn(List.of(relationship));

        // 푸시 발송 시 외부 예외 발생 시뮬레이션
        doThrow(new RuntimeException("Kakao Push Server Down"))
                .when(kakaoPushClient).sendPush(anyLong(), anyString(), any(), anyString());

        GpsResponse response = gpsService.saveLocation(1L, gpsRequest);

        // 예외가 삼켜지고 핵심 기능 정상 완결 확인
        assertThat(response).isNotNull();
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/location/1"), any(Object.class));
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
