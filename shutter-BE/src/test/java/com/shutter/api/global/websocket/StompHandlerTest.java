package com.shutter.api.global.websocket;

import com.shutter.api.global.jwt.JwtProvider;
import com.shutter.api.user.repository.UserRelationshipRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class StompHandlerTest {

    @InjectMocks
    private StompHandler stompHandler;

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private UserRelationshipRepository userRelationshipRepository;

    @Test
    @DisplayName("CONNECT 성공 - 유효한 JWT 토큰 시 세션에 userId 저장")
    void preSend_connect_validToken_setsUserIdInSession() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer valid_token");
        accessor.setSessionAttributes(new HashMap<>());
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        given(jwtProvider.validateToken("valid_token")).willReturn(true);
        given(jwtProvider.getUserId("valid_token")).willReturn(1L);

        Message<?> result = stompHandler.preSend(message, mock(MessageChannel.class));

        assertThat(result).isNotNull();
        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
        assertThat(resultAccessor.getSessionAttributes()).containsEntry("userId", 1L);
    }

    @Test
    @DisplayName("CONNECT 실패 - 헤더 누락 시 AccessDeniedException 발생")
    void preSend_connect_missingHeader_throwsAccessDenied() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionAttributes(new HashMap<>());
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> stompHandler.preSend(message, mock(MessageChannel.class)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("접근 권한이 없습니다.");
    }

    @Test
    @DisplayName("SUBSCRIBE 위치 토픽 성공 - 등록된 보호자의 피보호자 위치 구독 허용")
    void preSend_subscribe_locationTopic_guardian_success() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/location/100");
        Map<String, Object> sessionAttributes = new HashMap<>();
        sessionAttributes.put("userId", 200L); // 보호자 ID
        accessor.setSessionAttributes(sessionAttributes);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        given(userRelationshipRepository.existsByGuardianIdAndWardId(200L, 100L)).willReturn(true);

        Message<?> result = stompHandler.preSend(message, mock(MessageChannel.class));
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("SUBSCRIBE 위치 토픽 성공 - 피보호자 본인의 위치 구독 허용")
    void preSend_subscribe_locationTopic_self_success() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/location/100");
        Map<String, Object> sessionAttributes = new HashMap<>();
        sessionAttributes.put("userId", 100L); // 본인 ID
        accessor.setSessionAttributes(sessionAttributes);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = stompHandler.preSend(message, mock(MessageChannel.class));
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("SUBSCRIBE 위치 토픽 차단 - 무관계 제3자의 실시간 위치 도청 시도 시 AccessDeniedException 발생 (BOLA 방어)")
    void preSend_subscribe_locationTopic_unauthorizedUser_throwsAccessDenied() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/location/100");
        Map<String, Object> sessionAttributes = new HashMap<>();
        sessionAttributes.put("userId", 999L); // 제3자 ID
        accessor.setSessionAttributes(sessionAttributes);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        given(userRelationshipRepository.existsByGuardianIdAndWardId(999L, 100L)).willReturn(false);

        assertThatThrownBy(() -> stompHandler.preSend(message, mock(MessageChannel.class)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("해당 피보호자의 위치 정보를 구독할 권한이 없습니다.");
    }

    @Test
    @DisplayName("SUBSCRIBE 통화 토픽 차단 - 타인의 통화 채널 구독 시도 시 AccessDeniedException 발생")
    void preSend_subscribe_callTopic_unauthorizedUser_throwsAccessDenied() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/call/100");
        Map<String, Object> sessionAttributes = new HashMap<>();
        sessionAttributes.put("userId", 999L); // 타인 ID
        accessor.setSessionAttributes(sessionAttributes);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> stompHandler.preSend(message, mock(MessageChannel.class)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("본인의 통화 이벤트 채널만 구독할 수 있습니다.");
    }
}
