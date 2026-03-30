package com.shutter.api.gps.service;

import com.shutter.api.gps.dto.response.LocationExpiredDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.listener.KeyExpirationEventMessageListener;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RedisKeyExpirationListener extends KeyExpirationEventMessageListener {

    private final SimpMessagingTemplate messagingTemplate;

    public RedisKeyExpirationListener(RedisMessageListenerContainer listenerContainer, SimpMessagingTemplate messagingTemplate) {
        super(listenerContainer);
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String expiredKey = message.toString();
        log.debug("Redis Key Expired: {}", expiredKey);

        if (expiredKey.startsWith("location:")) {
            try {
                String wardIdStr = expiredKey.substring("location:".length());
                Long wardId = Long.parseLong(wardIdStr);

                LocationExpiredDto expiredDto = LocationExpiredDto.builder()
                        .wardId(wardId)
                        .isSharing(false)
                        .build();

                String destination = "/topic/location/" + wardId;
                messagingTemplate.convertAndSend(destination, expiredDto);
                log.debug("Published LOCATION_EXPIRED to {}", destination);
            } catch (NumberFormatException e) {
                log.error("Failed to parse wardId from expired Redis key: {}", expiredKey, e);
            }
        } else if (expiredKey.startsWith("call:")) {
            // 통화 수락 대기시간(TTL 30초) 만료 시 자동 취소용이므로 별도 처리 없이 무시
            log.debug("Call ringing session expired. Ignored in listener: {}", expiredKey);
        }
    }
}
