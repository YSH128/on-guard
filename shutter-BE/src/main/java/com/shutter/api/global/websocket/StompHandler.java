package com.shutter.api.global.websocket;

import com.shutter.api.global.jwt.JwtProvider;
import com.shutter.api.user.repository.UserRelationshipRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 99)
public class StompHandler implements ChannelInterceptor {

    private final JwtProvider jwtProvider;
    private final UserRelationshipRepository userRelationshipRepository;

    private static final String LOCATION_TOPIC_PREFIX = "/topic/location/";
    private static final String CALL_TOPIC_PREFIX = "/topic/call/";

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null) {
            if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                authenticateConnect(accessor);
            } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                authorizeSubscription(accessor);
            }
        }

        return message;
    }

    /**
     * CONNECT 프레임 인입 시 JWT Bearer 토큰 검증 및 세션 속성 바인딩
     */
    private void authenticateConnect(StompHeaderAccessor accessor) {
        String authorizationHeader = accessor.getFirstNativeHeader("Authorization");

        if (StringUtils.hasText(authorizationHeader) && authorizationHeader.startsWith("Bearer ")) {
            String token = authorizationHeader.substring(7);
            if (jwtProvider.validateToken(token)) {
                Long userId = jwtProvider.getUserId(token);
                accessor.getSessionAttributes().put("userId", userId);
                log.debug("WebSocket connect success for userId: {}", userId);
            } else {
                log.warn("WebSocket connect failed: invalid token");
                throw new AccessDeniedException("유효하지 않은 토큰입니다.");
            }
        } else {
            log.warn("WebSocket connect failed: missing or invalid Authorization header");
            throw new AccessDeniedException("접근 권한이 없습니다.");
        }
    }

    /**
     * SUBSCRIBE 프레임 인입 시 토픽별 인가(Authorization) 검증 (BOLA / IDOR 방어)
     */
    private void authorizeSubscription(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (!StringUtils.hasText(destination)) {
            return;
        }

        Object userIdObj = (accessor.getSessionAttributes() != null)
                ? accessor.getSessionAttributes().get("userId")
                : null;

        if (userIdObj == null) {
            log.warn("WebSocket subscribe rejected: unauthenticated session for destination: {}", destination);
            throw new AccessDeniedException("인증되지 않은 웹소켓 세션입니다.");
        }

        Long userId = (Long) userIdObj;

        // 1. 실시간 위치 토픽 인가 검증: /topic/location/{wardId}
        if (destination.startsWith(LOCATION_TOPIC_PREFIX)) {
            String wardIdStr = destination.substring(LOCATION_TOPIC_PREFIX.length());
            try {
                Long wardId = Long.parseLong(wardIdStr);
                // 본인 위치 구독이거나, 해당 피보호자의 등록된 보호자인지 검증
                boolean isSelf = userId.equals(wardId);
                boolean isGuardian = userRelationshipRepository.existsByGuardianIdAndWardId(userId, wardId);

                if (!isSelf && !isGuardian) {
                    log.warn("BOLA prevented: userId {} is not allowed to subscribe to location of wardId {}", userId, wardId);
                    throw new AccessDeniedException("해당 피보호자의 위치 정보를 구독할 권한이 없습니다.");
                }
                log.debug("WebSocket location subscription authorized: userId {} -> wardId {}", userId, wardId);
            } catch (NumberFormatException e) {
                log.warn("Invalid wardId in destination: {}", destination);
                throw new AccessDeniedException("잘못된 토픽 경로입니다.");
            }
        }
        // 2. 1:1 통화 이벤트 토픽 인가 검증: /topic/call/{targetUserId}
        else if (destination.startsWith(CALL_TOPIC_PREFIX)) {
            String targetUserIdStr = destination.substring(CALL_TOPIC_PREFIX.length());
            try {
                Long targetUserId = Long.parseLong(targetUserIdStr);
                // 본인에게 전달되는 통화 이벤트 토픽만 구독 가능
                if (!userId.equals(targetUserId)) {
                    log.warn("BOLA prevented: userId {} attempted to subscribe to call topic of userId {}", userId, targetUserId);
                    throw new AccessDeniedException("본인의 통화 이벤트 채널만 구독할 수 있습니다.");
                }
                log.debug("WebSocket call subscription authorized: userId {}", userId);
            } catch (NumberFormatException e) {
                log.warn("Invalid userId in call destination: {}", destination);
                throw new AccessDeniedException("잘못된 토픽 경로입니다.");
            }
        }
    }
}
