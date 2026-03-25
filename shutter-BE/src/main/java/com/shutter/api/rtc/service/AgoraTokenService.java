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

    // 아고라 토큰 만료 시간: 1시간 (3600초)
    private static final int TOKEN_EXPIRE_TIME_IN_SECONDS = 3600;

    /**
     * 명세된 형식에 따라 채널명 생성: call_{caller_id}_{callee_id}_{timestamp}
     */
    public String generateChannelName(Long callerId, Long calleeId) {
        return String.format("call_%d_%d_%d", callerId, calleeId, Instant.now().toEpochMilli());
    }

    /**
     * RtcTokenBuilder2를 이용한 토큰 발급 (Privilege = Publisher)
     */
    public String createToken(String channelName, int uid) {
        RtcTokenBuilder2 tokenBuilder = new RtcTokenBuilder2();

        String token = tokenBuilder.buildTokenWithUid(
                agoraProperties.getAppId(),
                agoraProperties.getAppCertificate(),
                channelName,
                uid,
                RtcTokenBuilder2.Role.ROLE_PUBLISHER,
                TOKEN_EXPIRE_TIME_IN_SECONDS, // tokenExpire (초 단위 상대값)
                TOKEN_EXPIRE_TIME_IN_SECONDS  // privilegeExpire (초 단위 상대값)
        );

        log.debug("Generated Agora Token for channel: {}", channelName);
        return token;
    }
}
