package com.shutter.api.rtc.service;

import com.shutter.api.global.exception.BusinessException;
import com.shutter.api.global.exception.CallException;
import com.shutter.api.global.exception.ErrorCode;
import com.shutter.api.notification.service.AlertService;
import com.shutter.api.rtc.dto.CallSession;
import com.shutter.api.rtc.dto.request.CallRequest;
import com.shutter.api.rtc.dto.response.CallEventResponse;
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
     * 1. 발신자/수신자 유효성 검증
     * 2. 발신자 락 및 수신자 락(Callee Busy Lock) 원자적 선점
     * 3. Agora 토큰 발급 (발신자 UID 바인딩)
     * 4. Redis 세션 저장 (TTL 60초)
     * 5. 사일런트 데이터 푸시 전송 (실패 시 원자적 롤백)
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

        // 5. 발신자 락 선점
        callSessionService.saveCallerLock(callerId, channelName);

        // 6. 수신자 락 선점 (상대방 통화 중 방어 - 실패 시 발신자 락 롤백)
        try {
            callSessionService.saveCalleeLock(calleeId, channelName);
        } catch (BusinessException e) {
            callSessionService.deleteCallerLock(callerId);
            throw e;
        }

        // 7. 발신자 전용 토큰 발급 (callerId 바인딩)
        String token = agoraTokenService.createToken(channelName, callerId);

        // 8. Redis 콜 세션 저장 (TTL 60초, 상태: RINGING)
        callSessionService.saveSession(channelName, callerId, calleeId);

        // 9. Data 전용 카카오 푸시 전송 (안드로이드 백그라운드 수신용)
        Map<String, String> payloadMap = new HashMap<>();
        payloadMap.put("type", "INCOMING_CALL");
        payloadMap.put("caller_id", String.valueOf(callerId));
        payloadMap.put("caller_kakao_id", String.valueOf(caller.getKakaoId()));
        payloadMap.put("caller_name", caller.getName());
        payloadMap.put("channel_name", channelName);

        try {
            alertService.sendDataAlertToUser(callee.getKakaoId(), payloadMap);
            log.info("Sent INCOMING_CALL data push to callee: {}, callerKakaoId: {}", calleeId, caller.getKakaoId());
        } catch (Exception e) {
            log.error("Failed to send incoming call push. Rolling back redis session & locks for channel: {}", channelName, e);
            callSessionService.deleteSession(channelName);
            callSessionService.deleteCallerLock(callerId);
            callSessionService.deleteCalleeLock(calleeId);
            throw e;
        }

        // 10. 응답 반환
        return CallResponse.builder()
                .channelName(channelName)
                .token(token)
                .build();
    }

    /**
     * 통화 수락(Accept) 기능 처리
     * 1. 세션 조회 및 수신자 본인 인가 검증
     * 2. 수신자 전용 Agora 토큰 발급 (calleeId 바인딩)
     * 3. 링잉 락 해제 및 세션 상태를 CONNECTED로 전환
     * 4. 푸시 및 WebSocket 브로드캐스트
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

        // 3. 수신자 전용 토큰 발급 (userId 바인딩)
        String token = agoraTokenService.createToken(channelName, userId);

        // 4. 발신자 조회 -> kakaoId 획득
        User caller = userRepository.findById(session.getCallerId())
                .orElseThrow(() -> new CallException(ErrorCode.USER_NOT_FOUND));

        // 5. 링잉 락 해제 및 세션 상태 CONNECTED 전환 (세션 유지)
        callSessionService.deleteCallerLock(caller.getId());
        callSessionService.deleteCalleeLock(userId);
        callSessionService.updateSessionStatus(channelName, CallSession.CallStatus.CONNECTED);

        // 6. Data 전용 카카오 푸시 전송 (백그라운드 대비)
        User callee = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Map<String, String> payloadMap = new HashMap<>();
        payloadMap.put("type", "CALL_ACCEPTED");
        payloadMap.put("channel_name", channelName);
        payloadMap.put("callee_kakao_id", String.valueOf(callee.getKakaoId()));

        try {
            alertService.sendDataAlertToUser(caller.getKakaoId(), payloadMap);
            log.info("Sent CALL_ACCEPTED data push to caller: {}, calleeKakaoId: {}", caller.getId(), callee.getKakaoId());
        } catch (Exception e) {
            log.warn("Failed to send CALL_ACCEPTED push to caller {}, but continuing flow.", caller.getId(), e);
        }

        // 7. WebSocket 이벤트 발송 (발신자에게 실시간 수락 알림)
        try {
            CallEventResponse eventResponse = CallEventResponse.builder()
                    .type("CALL_ACCEPTED")
                    .channelName(channelName)
                    .build();
            messagingTemplate.convertAndSend("/topic/call/" + caller.getId(), eventResponse);
            log.info("Published CALL_ACCEPTED via WebSocket to caller: {}", caller.getId());
        } catch (Exception e) {
            log.warn("Failed to send CALL_ACCEPTED websocket to caller {}", caller.getId(), e);
        }

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
        // 1. Redis 통화 세션 조회
        CallSession session = callSessionService.getSession(channelName);
        if (session == null) {
            throw new CallException(ErrorCode.CALL_NOT_FOUND);
        }

        // 2. 수신자 권한 확인 (userId != calleeId 이면 403)
        if (!userId.equals(session.getCalleeId())) {
            throw new CallException(ErrorCode.UNAUTHORIZED_ACCESS);
        }

        // 3. Redis 세션 삭제 및 양측 락 해제
        callSessionService.deleteSession(channelName);
        callSessionService.deleteCallerLock(session.getCallerId());
        callSessionService.deleteCalleeLock(userId);

        // 4. 발신자 조회 -> kakaoId 획득
        User caller = userRepository.findById(session.getCallerId())
                .orElseThrow(() -> new CallException(ErrorCode.USER_NOT_FOUND));

        // 5. Data 전용 카카오 푸시 전송
        User callee = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Map<String, String> payloadMap = new HashMap<>();
        payloadMap.put("type", "CALL_REJECTED");
        payloadMap.put("channel_name", channelName);
        payloadMap.put("callee_kakao_id", String.valueOf(callee.getKakaoId()));

        alertService.sendDataAlertToUser(caller.getKakaoId(), payloadMap);
        log.info("Sent CALL_REJECTED data push to caller: {}, calleeKakaoId: {}", caller.getId(), callee.getKakaoId());

        // 6. WebSocket 이벤트 발송 (발신자에게 거절 알림)
        CallEventResponse eventResponse = CallEventResponse.builder()
                .type("CALL_REJECTED")
                .channelName(channelName)
                .build();
        messagingTemplate.convertAndSend("/topic/call/" + caller.getId(), eventResponse);
        log.info("Published CALL_REJECTED via WebSocket to caller: {}", caller.getId());
    }

    /**
     * 통화 취소(Cancel) 기능 처리 (발신자가 응답 전에 취소하는 경우)
     */
    public void cancelCall(Long userId, String channelName) {
        // 1. Redis 통화 세션 조회
        CallSession session = callSessionService.getSession(channelName);
        if (session == null) {
            throw new CallException(ErrorCode.CALL_NOT_FOUND);
        }

        // 2. 발신자 권한 확인 (userId != callerId 이면 403)
        if (!userId.equals(session.getCallerId())) {
            throw new CallException(ErrorCode.UNAUTHORIZED_ACCESS);
        }

        // 3. Redis 세션 삭제 및 양측 락 해제
        callSessionService.deleteSession(channelName);
        callSessionService.deleteCallerLock(userId);
        callSessionService.deleteCalleeLock(session.getCalleeId());

        // 4. 수신자 조회 -> kakaoId 획득
        User callee = userRepository.findById(session.getCalleeId())
                .orElseThrow(() -> new CallException(ErrorCode.USER_NOT_FOUND));

        // 5. Data 전용 카카오 푸시 전송
        User caller = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Map<String, String> payloadMap = new HashMap<>();
        payloadMap.put("type", "CALL_CANCELED");
        payloadMap.put("channel_name", channelName);
        payloadMap.put("caller_kakao_id", String.valueOf(caller.getKakaoId()));

        alertService.sendDataAlertToUser(callee.getKakaoId(), payloadMap);
        log.info("Sent CALL_CANCELED data push to callee: {}, callerKakaoId: {}", callee.getId(), caller.getKakaoId());

        // 6. WebSocket 이벤트 발송 (수신자에게 취소 알림)
        CallEventResponse eventResponse = CallEventResponse.builder()
                .type("CALL_CANCELED")
                .channelName(channelName)
                .build();
        messagingTemplate.convertAndSend("/topic/call/" + callee.getId(), eventResponse);
        log.info("Published CALL_CANCELED via WebSocket to callee: {}", callee.getId());
    }

    /**
     * 통화 종료(End) 기능 처리 (통화 중 한쪽 사용자가 통화를 종료하는 경우)
     */
    public void endCall(Long userId, String channelName) {
        // 1. Redis 통화 세션 조회
        CallSession session = callSessionService.getSession(channelName);
        if (session == null) {
            log.info("Call session already ended or expired for channel: {}", channelName);
            return;
        }

        // 2. 통화 당사자(발신자 또는 수신자) 권한 확인
        boolean isCaller = userId.equals(session.getCallerId());
        boolean isCallee = userId.equals(session.getCalleeId());
        if (!isCaller && !isCallee) {
            throw new CallException(ErrorCode.UNAUTHORIZED_ACCESS);
        }

        Long targetUserId = isCaller ? session.getCalleeId() : session.getCallerId();

        // 3. Redis 세션 및 잔여 락 완전 삭제
        callSessionService.deleteSession(channelName);
        callSessionService.deleteCallerLock(session.getCallerId());
        callSessionService.deleteCalleeLock(session.getCalleeId());

        // 4. 상대방에게 통화 종료 알림 전달 (푸시 & 웹소켓)
        userRepository.findById(targetUserId).ifPresent(targetUser -> {
            Map<String, String> payloadMap = new HashMap<>();
            payloadMap.put("type", "CALL_ENDED");
            payloadMap.put("channel_name", channelName);

            try {
                alertService.sendDataAlertToUser(targetUser.getKakaoId(), payloadMap);
                log.info("Sent CALL_ENDED data push to targetUser: {}", targetUserId);
            } catch (Exception e) {
                log.warn("Failed to send CALL_ENDED push to targetUser {}", targetUserId, e);
            }

            try {
                CallEventResponse eventResponse = CallEventResponse.builder()
                        .type("CALL_ENDED")
                        .channelName(channelName)
                        .build();
                messagingTemplate.convertAndSend("/topic/call/" + targetUserId, eventResponse);
                log.info("Published CALL_ENDED via WebSocket to targetUser: {}", targetUserId);
            } catch (Exception e) {
                log.warn("Failed to send CALL_ENDED websocket to targetUser {}", targetUserId, e);
            }
        });

        log.info("Call successfully ended for channel: {}, endedBy: {}", channelName, userId);
    }
}
