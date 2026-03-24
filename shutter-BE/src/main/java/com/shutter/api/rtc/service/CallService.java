package com.shutter.api.rtc.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shutter.api.global.exception.CallException;
import com.shutter.api.global.exception.ErrorCode;
import com.shutter.api.notification.service.AlertService;
import com.shutter.api.rtc.dto.request.CallRequest;
import com.shutter.api.rtc.dto.response.CallResponse;
import com.shutter.api.user.domain.User;
import com.shutter.api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CallService {

    private final UserRepository userRepository;
    private final AgoraTokenService agoraTokenService;
    private final CallSessionService callSessionService;
    private final AlertService alertService;
    private final ObjectMapper objectMapper;

    /**
     * 통화 요청(Request) 기능 처리
     */
    public CallResponse requestCall(Long callerId, CallRequest request) {
        Long calleeId = request.getCalleeId();

        // 1. 발신자 조회 -> kakaoId 혹은 name 획득
        User caller = userRepository.findById(callerId)
                .orElseThrow(() -> new CallException(ErrorCode.USER_NOT_FOUND));

        // 2. 수신자 조회 (없으면 CALL_NOT_FOUND)
        User callee = userRepository.findById(calleeId)
                .orElseThrow(() -> new CallException(ErrorCode.CALL_NOT_FOUND));

        // 3. 본인에게 통화 요청 방지
        if (callerId.equals(calleeId)) {
            throw new CallException(ErrorCode.SELF_CALL_NOT_ALLOWED);
        }

        // 4. 채널명 생성
        String channelName = agoraTokenService.generateChannelName(callerId, calleeId);
        
        // 5. 토큰 발급 (uid는 callerId)
        int uid = callerId.intValue();
        String token = agoraTokenService.createToken(channelName, uid);
        
        // 6. Redis 콜 세션 저장 (TTL 30초)
        callSessionService.saveSession(channelName, callerId, calleeId);
        
        // 7. 카카오 푸시 전송 (AlertService.sendAlertToUser)
        Map<String, Object> payloadMap = new HashMap<>();
        payloadMap.put("type", "INCOMING_CALL");
        payloadMap.put("caller_id", callerId);
        payloadMap.put("caller_name", caller.getName());
        payloadMap.put("channel_name", channelName);

        try {
            String payloadJson = objectMapper.writeValueAsString(payloadMap);
            alertService.sendAlertToUser(callee.getKakaoId(), payloadJson);
            log.info("Sent CALL push payload to callee: {}", calleeId);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize push payload for caller: {}", callerId, e);
            throw new CallException(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        // 8. 응답 반환
        return CallResponse.builder()
                .channelName(channelName)
                .token(token)
                .build();
    }
}
