package com.shutter.api.rtc.service;

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
import org.springframework.messaging.simp.SimpMessagingTemplate;
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
    private final SimpMessagingTemplate messagingTemplate;

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
                .orElseThrow(() -> new CallException(ErrorCode.CALLEE_NOT_FOUND));

        Long calleeId = callee.getId();

        // 3. 본인에게 통화 요청 방지
        if (callerId.equals(calleeId)) {
            throw new CallException(ErrorCode.SELF_CALL_NOT_ALLOWED);
        }

        // 4. 채널명 생성
        String channelName = agoraTokenService.generateChannelName(callerId, calleeId);
        
        // 5. 중복 호출 방지를 위한 발신자 락 저장 (따닥 방지)
        callSessionService.saveCallerLock(callerId, channelName);
        
        // 6. 토큰 발급 (uid=0: 어떤 uid로도 join 가능한 범용 토큰)
        String token = agoraTokenService.createToken(channelName, 0);
        
        // 7. Redis 콜 세션 저장 (TTL 60초)
        callSessionService.saveSession(channelName, callerId, calleeId);
        
        // 7. Data 전용 카카오 푸시 전송 (안드로이드 백그라운드 수신용)
        Map<String, String> payloadMap = new HashMap<>();
        payloadMap.put("type", "INCOMING_CALL");
        payloadMap.put("caller_id", String.valueOf(callerId));
        payloadMap.put("caller_name", caller.getName());
        payloadMap.put("channel_name", channelName);

        alertService.sendDataAlertToUser(callee.getKakaoId(), payloadMap);
        log.info("Sent INCOMING_CALL data push to callee: {}", calleeId);

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

        // 3. 토큰 발급 (uid=0: 어떤 uid로도 join 가능한 범용 토큰)
        String token = agoraTokenService.createToken(channelName, 0);

        // 4. 발신자 조회 -> kakaoId 획득
        User caller = userRepository.findById(session.getCallerId())
                .orElseThrow(() -> new CallException(ErrorCode.USER_NOT_FOUND));

        // 6. Data 전용 카카오 푸시 전송 (안드로이드 백그라운드 수신용)
        Map<String, String> payloadMap = new HashMap<>();
        payloadMap.put("type", "CALL_ACCEPTED");
        payloadMap.put("channel_name", channelName);

        alertService.sendDataAlertToUser(caller.getKakaoId(), payloadMap);
        log.info("Sent CALL_ACCEPTED data push to caller: {}", caller.getId());

        // WebSocket 이벤트 발송 (발신자에게 수락 알림)
        com.shutter.api.rtc.dto.response.CallEventResponse eventResponse = com.shutter.api.rtc.dto.response.CallEventResponse.builder()
                .type("CALL_ACCEPTED")
                .channelName(channelName)
                .build();
        messagingTemplate.convertAndSend("/topic/call/" + caller.getId(), eventResponse);
        log.info("Published CALL_ACCEPTED via WebSocket to caller: {}", caller.getId());

        // 7. Redis 세션 삭제 및 발신자 락 해제 (푸시 성공 후 삭제)
        callSessionService.deleteSession(channelName);
        callSessionService.deleteCallerLock(caller.getId());

        // 8. 결과 반환
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

        // 3. Redis 세션 삭제 및 발신자 락 해제
        callSessionService.deleteSession(channelName);
        callSessionService.deleteCallerLock(session.getCallerId());

        // 4. 발신자 조회 -> kakaoId 획득
        User caller = userRepository.findById(session.getCallerId())
                .orElseThrow(() -> new CallException(ErrorCode.USER_NOT_FOUND));

        // 5. Data 전용 카카오 푸시 전송 (안드로이드 백그라운드 수신용)
        Map<String, String> payloadMap = new HashMap<>();
        payloadMap.put("type", "CALL_REJECTED");
        payloadMap.put("channel_name", channelName);

        alertService.sendDataAlertToUser(caller.getKakaoId(), payloadMap);
        log.info("Sent CALL_REJECTED data push to caller: {}", caller.getId());

        // WebSocket 이벤트 발송 (발신자에게 거절 알림)
        com.shutter.api.rtc.dto.response.CallEventResponse eventResponse = com.shutter.api.rtc.dto.response.CallEventResponse.builder()
                .type("CALL_REJECTED")
                .channelName(channelName)
                .build();
        messagingTemplate.convertAndSend("/topic/call/" + caller.getId(), eventResponse);
        log.info("Published CALL_REJECTED via WebSocket to caller: {}", caller.getId());
    }
}
