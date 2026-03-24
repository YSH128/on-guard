package com.shutter.api.rtc.service;

import com.shutter.api.global.config.AgoraProperties;
import io.agora.media.RtcTokenBuilder2;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgoraTokenService {

    private final AgoraProperties agoraProperties;
    private final CallSessionService callSessionService;

    // 아고라 토큰 만료 시간: 1시간 (3600초)
    private static final int TOKEN_EXPIRE_TIME_IN_SECONDS = 3600;

    /**
     * 채널명을 생성하고, Redis에 30초 TTL로 통화 세션 정보를 기록한 뒤 RTC 토큰을 발급한다.
     * @param callerId 발신자 ID
     * @param calleeId 수신자 ID
     * @param uid 아고라 내부 식별자 (보통 0으로 하거나 우리 서비스의 고유 ID 사용)
     * @return 발급된 토큰
     */
    public String generateTokenAndSaveSession(Long callerId, Long calleeId, int uid) {
        String channelName = generateChannelName(callerId, calleeId);

        // Redis에 통화 세션 정보 저장 (CallSessionService 위임)
        callSessionService.saveSession(channelName, callerId, calleeId);

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
