package com.shutter.api.rtc.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shutter.api.global.exception.CallException;
import com.shutter.api.global.exception.ErrorCode;
import com.shutter.api.notification.service.AlertService;
import com.shutter.api.rtc.dto.CallSession;
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
        Long calleeKakaoId = request.getCalleeKakaoId();

        // 1. 발신자 조회
        User caller = userRepository.findById(callerId)
                .orElseThrow(() -> new CallException(ErrorCode.USER_NOT_FOUND));

        // 2. 수신자 조회 (카카오 ID로 매핑)
        User callee = userRepository.findByKakaoId(calleeKakaoId)
                .orElseThrow(() -> new CallException(ErrorCode.USER_NOT_FOUND));

        Long calleeId = callee.getId();

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

    /**
     * 통화 수락(Accept) 기능 처리
     */
    public CallResponse acceptCall(Long userId, String channelName) {
        // 1. Redis 통화 세션 조회 (없으면 CALL_NOT_FOUND)
        CallSession session = callSessionService.getSession(channelName);
        if (session == null) {
            throw new CallException(ErrorCode.CALL_NOT_FOUND);
        }

        // 2. 수신자 권한 확인 (userId != calleeId 이면 403)
        if (!userId.equals(session.getCalleeId())) {
            throw new CallException(ErrorCode.UNAUTHORIZED_ACCESS);
        }

        // 3. 토큰 발급 (수신자용)
        int uid = userId.intValue();
        String token = agoraTokenService.createToken(channelName, uid);

        // 4. Redis 세션 삭제 (수락되었으므로 링잉(대기) 상태 해제)
        callSessionService.deleteSession(channelName);

        // 5. 발신자 조회 -> kakaoId 획득
        User caller = userRepository.findById(session.getCallerId())
                .orElseThrow(() -> new CallException(ErrorCode.USER_NOT_FOUND));

        // 6. 카카오 푸시 전송 (AlertService.sendAlertToUser)
        Map<String, Object> payloadMap = new HashMap<>();
        payloadMap.put("type", "CALL_ACCEPTED");
        payloadMap.put("channel_name", channelName);

        try {
            String payloadJson = objectMapper.writeValueAsString(payloadMap);
            alertService.sendAlertToUser(caller.getKakaoId(), payloadJson);
            log.info("Sent CALL_ACCEPTED push to caller: {}", caller.getId());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize push payload for caller: {}", caller.getId(), e);
            throw new CallException(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        // 7. 결과 반환
        return CallResponse.builder()
                .channelName(channelName)
                .token(token)
                .build();
    }

    /**
     * 통화 거절(Reject) 기능 처리
     */
    public void rejectCall(Long userId, String channelName) {
        // 1. Redis 통화 세션 조회 (없으면 CALL_NOT_FOUND)
        CallSession session = callSessionService.getSession(channelName);
        if (session == null) {
            throw new CallException(ErrorCode.CALL_NOT_FOUND);
        }

        // 2. 수신자 권한 확인 (userId != calleeId 이면 403)
        if (!userId.equals(session.getCalleeId())) {
            throw new CallException(ErrorCode.UNAUTHORIZED_ACCESS);
        }

        // 3. Redis 세션 삭제
        callSessionService.deleteSession(channelName);

        // 4. 발신자 조회 -> kakaoId 획득
        User caller = userRepository.findById(session.getCallerId())
                .orElseThrow(() -> new CallException(ErrorCode.USER_NOT_FOUND));

        // 5. 카카오 푸시 전송 (AlertService.sendAlertToUser)
        Map<String, Object> payloadMap = new HashMap<>();
        payloadMap.put("type", "CALL_REJECTED");
        payloadMap.put("channel_name", channelName);

        try {
            String payloadJson = objectMapper.writeValueAsString(payloadMap);
            alertService.sendAlertToUser(caller.getKakaoId(), payloadJson);
            log.info("Sent CALL_REJECTED push to caller: {}", caller.getId());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize push payload for caller: {}", caller.getId(), e);
            throw new CallException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}
