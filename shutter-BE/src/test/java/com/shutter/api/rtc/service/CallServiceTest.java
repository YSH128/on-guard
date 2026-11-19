package com.shutter.api.rtc.service;

import com.shutter.api.global.exception.BusinessException;
import com.shutter.api.global.exception.CallException;
import com.shutter.api.global.exception.ErrorCode;
import com.shutter.api.notification.service.AlertService;
import com.shutter.api.rtc.dto.CallSession;
import com.shutter.api.rtc.dto.request.CallRequest;
import com.shutter.api.rtc.dto.response.CallEventResponse;
import com.shutter.api.rtc.dto.response.CallResponse;
import com.shutter.api.user.domain.Role;
import com.shutter.api.user.domain.User;
import com.shutter.api.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class CallServiceTest {

    @InjectMocks
    private CallService callService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AgoraTokenService agoraTokenService;

    @Mock
    private CallSessionService callSessionService;

    @Mock
    private AlertService alertService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private User caller;
    private User callee;

    @BeforeEach
    void setUp() {
        caller = User.builder()
                .kakaoId(1001L)
                .name("발신자")
                .deviceUuid("caller-device-uuid")
                .build();
        caller.assignRole(Role.WARD);
        ReflectionTestUtils.setField(caller, "id", 1L);

        callee = User.builder()
                .kakaoId(2002L)
                .name("수신자")
                .deviceUuid("callee-device-uuid")
                .build();
        callee.assignRole(Role.GUARDIAN);
        ReflectionTestUtils.setField(callee, "id", 2L);
    }

    @Test
    @DisplayName("통화 요청 성공 - 발신자/수신자 락 동시 획득 및 발신자 UID 바인딩 토큰 발급")
    void requestCall_success_acquiresBothLocksAndBindsCallerUid() {
        // given
        Long callerId = 1L;
        Long calleeKakaoId = 2002L;
        CallRequest request = new CallRequest(calleeKakaoId);
        String channelName = "channel_1_2_123456";
        String expectedToken = "agora_token_for_caller_1";

        given(userRepository.findById(callerId)).willReturn(Optional.of(caller));
        given(userRepository.findByKakaoId(calleeKakaoId)).willReturn(Optional.of(callee));
        given(agoraTokenService.generateChannelName(callerId, 2L)).willReturn(channelName);
        given(agoraTokenService.createToken(channelName, callerId)).willReturn(expectedToken);

        // when
        CallResponse response = callService.requestCall(callerId, request);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getChannelName()).isEqualTo(channelName);
        assertThat(response.getToken()).isEqualTo(expectedToken);

        // 락 선점 및 세션 저장 확인
        then(callSessionService).should().saveCallerLock(callerId, channelName);
        then(callSessionService).should().saveCalleeLock(2L, channelName);
        then(callSessionService).should().saveSession(channelName, callerId, 2L);
        then(alertService).should().sendDataAlertToUser(eq(calleeKakaoId), anyMap());
    }

    @Test
    @DisplayName("통화 요청 실패 - 수신자가 이미 다른 통화 중이면 CALLEE_ALREADY_BUSY 발생 및 발신자 락 롤백")
    void requestCall_calleeBusy_rollsBackCallerLockAndThrows409() {
        // given
        Long callerId = 1L;
        Long calleeKakaoId = 2002L;
        CallRequest request = new CallRequest(calleeKakaoId);
        String channelName = "channel_1_2_123456";

        given(userRepository.findById(callerId)).willReturn(Optional.of(caller));
        given(userRepository.findByKakaoId(calleeKakaoId)).willReturn(Optional.of(callee));
        given(agoraTokenService.generateChannelName(callerId, 2L)).willReturn(channelName);

        // 수신자 락 선점 시 이미 통화 중 예외 발생 시뮬레이션
        willThrow(new BusinessException(ErrorCode.CALLEE_ALREADY_BUSY))
                .given(callSessionService).saveCalleeLock(2L, channelName);

        // when & then
        assertThatThrownBy(() -> callService.requestCall(callerId, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CALLEE_ALREADY_BUSY);

        // 발신자 락 획득 후 즉시 롤백 해제되었는지 검증
        then(callSessionService).should().saveCallerLock(callerId, channelName);
        then(callSessionService).should().deleteCallerLock(callerId);

        // 세션 저장 및 푸시 발송은 진행되지 않아야 함
        then(callSessionService).should(never()).saveSession(any(), any(), any());
        then(alertService).should(never()).sendDataAlertToUser(any(), anyMap());
    }

    @Test
    @DisplayName("통화 요청 실패 - 푸시 발송 실패 시 발신자/수신자 락 및 세션 원자적 롤백")
    void requestCall_pushFails_rollsBackBothLocksAndSession() {
        // given
        Long callerId = 1L;
        Long calleeKakaoId = 2002L;
        CallRequest request = new CallRequest(calleeKakaoId);
        String channelName = "channel_1_2_123456";
        String token = "agora_token";

        given(userRepository.findById(callerId)).willReturn(Optional.of(caller));
        given(userRepository.findByKakaoId(calleeKakaoId)).willReturn(Optional.of(callee));
        given(agoraTokenService.generateChannelName(callerId, 2L)).willReturn(channelName);
        given(agoraTokenService.createToken(channelName, callerId)).willReturn(token);

        willThrow(new RuntimeException("FCM Push failed"))
                .given(alertService).sendDataAlertToUser(eq(calleeKakaoId), anyMap());

        // when & then
        assertThatThrownBy(() -> callService.requestCall(callerId, request))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("FCM Push failed");

        // 세션 및 양측 락 롤백 삭제 검증
        then(callSessionService).should().deleteSession(channelName);
        then(callSessionService).should().deleteCallerLock(callerId);
        then(callSessionService).should().deleteCalleeLock(2L);
    }

    @Test
    @DisplayName("통화 수락 성공 - 세션 상태 CONNECTED 전환, 링잉 락 해제 및 수신자 UID 바인딩 토큰 발급")
    void acceptCall_success_updatesSessionToConnectedAndReleasesLocks() {
        // given
        String channelName = "channel_1_2_123456";
        Long calleeId = 2L;
        Long callerId = 1L;
        CallSession session = CallSession.builder()
                .callerId(callerId)
                .calleeId(calleeId)
                .status(CallSession.CallStatus.RINGING)
                .createdAt(LocalDateTime.now())
                .build();

        given(callSessionService.getSession(channelName)).willReturn(session);
        given(agoraTokenService.createToken(channelName, calleeId)).willReturn("agora_token_for_callee_2");
        given(userRepository.findById(callerId)).willReturn(Optional.of(caller));
        given(userRepository.findById(calleeId)).willReturn(Optional.of(callee));

        // when
        CallResponse response = callService.acceptCall(calleeId, channelName);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getToken()).isEqualTo("agora_token_for_callee_2");

        // 링잉 락 해제 및 세션 상태 갱신 검증
        then(callSessionService).should().deleteCallerLock(callerId);
        then(callSessionService).should().deleteCalleeLock(calleeId);
        then(callSessionService).should().updateSessionStatus(channelName, CallSession.CallStatus.CONNECTED);

        // 세션 자체를 삭제하지 않고 유지했는지 검증 (AS-IS는 deleteSession 호출했었음)
        then(callSessionService).should(never()).deleteSession(channelName);

        // 푸시 및 웹소켓 알림 검증
        then(alertService).should().sendDataAlertToUser(eq(caller.getKakaoId()), anyMap());
        then(messagingTemplate).should().convertAndSend(eq("/topic/call/" + callerId), any(CallEventResponse.class));
    }

    @Test
    @DisplayName("통화 수락 실패 - 비인가 사용자(제3자)의 수락 요청 시 403 UNAUTHORIZED_ACCESS 발생")
    void acceptCall_unauthorizedUser_throws403() {
        // given
        String channelName = "channel_1_2_123456";
        Long unauthorizedUserId = 999L;
        CallSession session = CallSession.builder()
                .callerId(1L)
                .calleeId(2L)
                .status(CallSession.CallStatus.RINGING)
                .build();

        given(callSessionService.getSession(channelName)).willReturn(session);

        // when & then
        assertThatThrownBy(() -> callService.acceptCall(unauthorizedUserId, channelName))
                .isInstanceOf(CallException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHORIZED_ACCESS);
    }

    @Test
    @DisplayName("통화 종료 성공 - 세션 및 잔여 락 정리, 상대방에게 CALL_ENDED 웹소켓/푸시 알림 전송")
    void endCall_success_cleansUpSessionAndNotifiesOpponent() {
        // given
        String channelName = "channel_1_2_123456";
        Long callerId = 1L;
        Long calleeId = 2L;
        CallSession session = CallSession.builder()
                .callerId(callerId)
                .calleeId(calleeId)
                .status(CallSession.CallStatus.CONNECTED)
                .build();

        given(callSessionService.getSession(channelName)).willReturn(session);
        given(userRepository.findById(calleeId)).willReturn(Optional.of(callee));

        // when (발신자가 통화 종료 요청)
        callService.endCall(callerId, channelName);

        // then
        // 세션 및 락 정리 검증
        then(callSessionService).should().deleteSession(channelName);
        then(callSessionService).should().deleteCallerLock(callerId);
        then(callSessionService).should().deleteCalleeLock(calleeId);

        // 상대방(callee)에게 푸시 및 웹소켓 알림 전달 검증
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        then(alertService).should().sendDataAlertToUser(eq(callee.getKakaoId()), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue()).containsEntry("type", "CALL_ENDED");

        ArgumentCaptor<CallEventResponse> eventCaptor = ArgumentCaptor.forClass(CallEventResponse.class);
        then(messagingTemplate).should().convertAndSend(eq("/topic/call/" + calleeId), eventCaptor.capture());
        assertThat(eventCaptor.getValue().getType()).isEqualTo("CALL_ENDED");
    }
}
