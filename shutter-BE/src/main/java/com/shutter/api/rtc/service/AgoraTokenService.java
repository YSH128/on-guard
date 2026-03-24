package com.shutter.api.rtc.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shutter.api.global.config.AgoraProperties;
import com.shutter.api.global.exception.BusinessException;
import com.shutter.api.global.exception.ErrorCode;
import io.agora.media.RtcTokenBuilder2;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgoraTokenService {

    private final AgoraProperties agoraProperties;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    // 아고라 토큰 만료 시간: 1시간 (3600초)
    private static final int TOKEN_EXPIRE_TIME_IN_SECONDS = 3600;
    
    // 통화 생성 수락 대기시간 (Redis TTL): 30초
    private static final int CALL_RINGING_TTL_SECONDS = 30;

    /**
     * 채널명을 생성하고, Redis에 30초 TTL로 통화 세션 정보를 기록한 뒤 RTC 토큰을 발급한다.
     * @param callerId 발신자 ID
     * @param calleeId 수신자 ID
     * @param uid 아고라 내부 식별자 (보통 0으로 하거나 우리 서비스의 고유 ID 사용)
     * @return 발급된 토큰 
     */
    public String generateTokenAndSaveSession(Long callerId, Long calleeId, int uid) {
        String channelName = generateChannelName(callerId, calleeId);
        
        // Redis에 저장할 JSON Value 생성
        Map<String, Long> sessionData = new HashMap<>();
        sessionData.put("caller_id", callerId);
        sessionData.put("callee_id", calleeId);
        
        try {
            String valueJson = objectMapper.writeValueAsString(sessionData);
            String redisKey = "call:" + channelName;
            
            // Redis에 채널 정보 30초간 저장
            redisTemplate.opsForValue().set(redisKey, valueJson, CALL_RINGING_TTL_SECONDS, TimeUnit.SECONDS);
            log.info("Saved call session in Redis [Key: {}, TTL: {}s]", redisKey, CALL_RINGING_TTL_SECONDS);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize call session data for caller: {}, callee: {}", callerId, calleeId, e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        // Agora Token 생성
        return createRtcToken(channelName, uid);
    }

    /**
     * 명세된 형식에 따라 채널명 생성: call_{caller_id}_{callee_id}_{timestamp}
     */
    public String generateChannelName(Long callerId, Long calleeId) {
        return String.format("call_%d_%d_%d", callerId, calleeId, Instant.now().toEpochMilli());
    }

    /**
     * RtcTokenBuilder2를 이용한 토큰 발급 (Privilege = Publisher)
     */
    private String createRtcToken(String channelName, int uid) {
        RtcTokenBuilder2 tokenBuilder = new RtcTokenBuilder2();
        
        int timestamp = (int)(System.currentTimeMillis() / 1000 + TOKEN_EXPIRE_TIME_IN_SECONDS);

        String token = tokenBuilder.buildTokenWithUid(
                agoraProperties.getAppId(),
                agoraProperties.getAppCertificate(),
                channelName,
                uid,
                RtcTokenBuilder2.Role.ROLE_PUBLISHER,
                timestamp, // tokenExpireTs
                timestamp  // privilegeExpireTs
        );
        
        log.debug("Generated Agora Token for channel: {}", channelName);
        return token;
    }
}
