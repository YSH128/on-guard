package com.shutter.api.rtc.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shutter.api.global.exception.BusinessException;
import com.shutter.api.global.exception.ErrorCode;
import com.shutter.api.rtc.dto.CallSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class CallSessionService {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String REDIS_KEY_PREFIX = "call:";
    private static final String REDIS_LOCK_PREFIX = "call:lock:";
    private static final int CALL_RINGING_TTL_SECONDS = 60;

    /**
     * 채널명으로 통화 세션을 생성하고 Redis에 30초 TTL로 기록.
     */
    public void saveSession(String channelName, Long callerId, Long calleeId) {
        String key = REDIS_KEY_PREFIX + channelName;
        
        CallSession session = CallSession.builder()
                .callerId(callerId)
                .calleeId(calleeId)
                .build();
                
        try {
            String valueJson = objectMapper.writeValueAsString(session);
            redisTemplate.opsForValue().set(key, valueJson, CALL_RINGING_TTL_SECONDS, TimeUnit.SECONDS);
            log.info("Saved call session in Redis [Key: {}, TTL: {}s]", key, CALL_RINGING_TTL_SECONDS);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize CallSession for {}, callerId: {}, calleeId: {}", key, callerId, calleeId, e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Redis에서 통화 세션을 조회한다. 만료되었거나 없을 경우 null 반환.
     */
    public CallSession getSession(String channelName) {
        String key = REDIS_KEY_PREFIX + channelName;
        String valueJson = redisTemplate.opsForValue().get(key);
        
        if (valueJson == null) {
            return null;
        }
        
        try {
            return objectMapper.readValue(valueJson, CallSession.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize CallSession for {}", key, e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR); // 필요시 별도 커스텀 전용 에러로 변경 가능
        }
    }

    /**
     * Redis에서 통화 세션을 삭제한다 (통화 취소, 수락 등에 활용).
     */
    public void deleteSession(String channelName) {
        String key = REDIS_KEY_PREFIX + channelName;
        redisTemplate.delete(key);
        log.info("Deleted call session in Redis [Key: {}]", key);
    }

    /**
     * 발신자가 이미 전화를 걸고 있는지 확인 후 락 생성
     */
    public void saveCallerLock(Long callerId, String channelName) {
        String key = REDIS_LOCK_PREFIX + callerId;
        Boolean isSet = redisTemplate.opsForValue().setIfAbsent(key, channelName, CALL_RINGING_TTL_SECONDS, TimeUnit.SECONDS);

        if (Boolean.FALSE.equals(isSet)) {
            log.warn("Concurrent call detected for callerId: {}", callerId);
            throw new BusinessException(ErrorCode.CALL_ALREADY_IN_PROGRESS);
        }
    }

    /**
     * 발신자 락 해제 (수락, 거절 등)
     */
    public void deleteCallerLock(Long callerId) {
        String key = REDIS_LOCK_PREFIX + callerId;
        redisTemplate.delete(key);
        log.info("Deleted caller lock in Redis [Key: {}]", key);
    }
}
