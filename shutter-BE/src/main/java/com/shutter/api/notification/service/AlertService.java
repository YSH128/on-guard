package com.shutter.api.notification.service;

import com.shutter.api.global.exception.AlertException;
import com.shutter.api.global.exception.BusinessException;
import com.shutter.api.global.exception.ErrorCode;
import com.shutter.api.notification.client.KakaoPushClient;
import com.shutter.api.notification.domain.NotificationLog;
import com.shutter.api.notification.domain.NotificationStatus;
import com.shutter.api.notification.domain.NotificationEventType;
import com.shutter.api.notification.domain.PushResult;
import com.shutter.api.notification.dto.request.AlertRequest;
import com.shutter.api.notification.dto.response.AlertLogResponse;
import com.shutter.api.notification.dto.response.AlertLogsResponse;
import com.shutter.api.notification.dto.response.TimelineItemResponse;
import com.shutter.api.notification.dto.response.TimelineListResponse;
import com.shutter.api.notification.repository.NotificationLogRepository;
import com.shutter.api.user.domain.User;
import com.shutter.api.user.domain.UserRelationship;
import com.shutter.api.user.repository.UserRelationshipRepository;
import com.shutter.api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 알림(Alert) 관련 비즈니스 로직 처리 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {

    private final UserRepository userRepository;
    private final UserRelationshipRepository userRelationshipRepository;
    private final NotificationLogRepository notificationLogRepository;
    private final KakaoPushClient kakaoPushClient;

    /**
     * 알림 그룹 전송 및 로그 저장
     *
     * @param wardId     피보호자 ID (JWT 추출)
     * @param request    알림 요청 정보 (이벤트 타입, 발신자 등)
     * @return 저장된 알림 로그 목록
     */
    @Transactional(noRollbackFor = AlertException.class)
    public List<AlertLogResponse> sendAlertsToGuardians(Long wardId, AlertRequest request) {
        // 1. 피보호자 존재 여부 확인
        User ward = userRepository.findById(wardId)
                .orElseThrow(() -> new AlertException(ErrorCode.WARD_NOT_FOUND));

        // 2. 보호자 목록 조회 (관계 테이블)
        List<UserRelationship> relationships = userRelationshipRepository.findByWardIdWithGuardian(wardId);
        
        if (relationships.isEmpty()) {
            throw new AlertException(ErrorCode.NO_GUARDIANS_REGISTERED);
        }

        // 3. 알림 메시지 생성 (앱에서 피싱 유형을 보냈다면 메시지 안에 포함)
        String pushMessage = request.getEventType().getPushMessage(ward.getName(), request.getPhishingType());

        boolean hasFailure = false;
        List<NotificationLog> savedLogs = new java.util.ArrayList<>();

        // 4. 각 보호자에게 푸시 발송 및 로그 저장
        for (UserRelationship rel : relationships) {
            User guardian = rel.getGuardian();
            PushResult pushResult = kakaoPushClient.sendPush(guardian.getKakaoId(), request.getEventType(), pushMessage);
            boolean pushSuccess = (pushResult == PushResult.SUCCESS);

            NotificationLog notificationLog = NotificationLog.builder()
                    .ward(ward)
                    .guardian(guardian)
                    .caller(request.getCaller())
                    .eventType(request.getEventType())
                    .callStartedAt(request.getCallStartedAt())
                    .status(pushSuccess ? NotificationStatus.SUCCESS : NotificationStatus.FAIL)
                    .failReason(pushSuccess ? null : "Kakao Push API 호출 실패 (" + pushResult.name() + ")")
                    .build();

            NotificationLog savedLog = notificationLogRepository.save(notificationLog);
            savedLogs.add(savedLog);
            
            if (pushResult == PushResult.CONFIG_ERROR) {
                log.error("CRITICAL: Kakao Push configuration error detected.");
                throw new AlertException(ErrorCode.KAKAO_PUSH_CONFIG_ERROR);
            }

            if (!pushSuccess) {
                hasFailure = true;
            }
            
            log.info("Alert sent from ward {} to guardian {}. Status: {}", wardId, guardian.getId(), savedLog.getStatus());
        }

        // 5. 하나라도 실패했다면 최종적으로 에러 반환
        if (hasFailure) {
            throw new AlertException(ErrorCode.FCM_SEND_FAILED);
        }

        return savedLogs.stream().map(AlertLogResponse::from).collect(Collectors.toList());
    }

    /**
     * 피보호자의 알림 로그 목록 조회 (기존 API용)
     */
    @Transactional(readOnly = true)
    public AlertLogsResponse getAlertLogs(Long wardId) {
        List<NotificationLog> logs = getLogsByWardId(wardId);
        
        List<AlertLogResponse> responses = logs.stream()
                .map(AlertLogResponse::from)
                .collect(Collectors.toList());
        
        return AlertLogsResponse.from(responses);
    }

    /**
     * 단일 대상에게 커스텀 푸시 메시지(JSON) 전송
     */
    public void sendAlertToUser(Long kakaoId, String payloadJson) {
        PushResult pushResult = kakaoPushClient.sendPush(kakaoId, NotificationEventType.MESSAGE, payloadJson);

        if (pushResult == PushResult.CONFIG_ERROR) {
            log.error("❌ [PUSH FAIL] kakaoId={}, payload={}, error=KAKAO_PUSH_CONFIG_ERROR", kakaoId, payloadJson);
            throw new AlertException(ErrorCode.KAKAO_PUSH_CONFIG_ERROR);
        }

        if (pushResult != PushResult.SUCCESS) {
            log.error("❌ [PUSH FAIL] kakaoId={}, payload={}, error={}", kakaoId, payloadJson, pushResult);
            throw new AlertException(ErrorCode.FCM_SEND_FAILED);
        }

        log.info("✅ [PUSH SUCCESS] kakaoId={}, payload={}", kakaoId, payloadJson);
    }

    /**
     * 단일 대상에게 데이터 전용(사일런트) 푸시 전송 (FCM 백그라운드에서 직접 수신)
     */
    public void sendDataAlertToUser(Long kakaoId, Map<String, String> payload) {
        PushResult pushResult = kakaoPushClient.sendDataPush(kakaoId, payload);

        if (pushResult == PushResult.CONFIG_ERROR) {
            log.error("❌ [DATA PUSH FAIL] kakaoId={}, payload={}, error=KAKAO_PUSH_CONFIG_ERROR", kakaoId, payload);
            throw new AlertException(ErrorCode.KAKAO_PUSH_CONFIG_ERROR);
        }

        if (pushResult != PushResult.SUCCESS) {
            log.error("❌ [DATA PUSH FAIL] kakaoId={}, payload={}, error={}", kakaoId, payload, pushResult);
            throw new AlertException(ErrorCode.FCM_SEND_FAILED);
        }

        log.info("✅ [DATA PUSH SUCCESS] kakaoId={}, payload={}", kakaoId, payload);
    }

    /**
     * 피보호자의 타임라인 조회 (프론트 규격)
     */
    @Transactional(readOnly = true)
    public TimelineListResponse getTimeline(Long wardId) {
        List<NotificationLog> logs = getLogsByWardId(wardId);

        List<TimelineItemResponse> timelineItems = new java.util.ArrayList<>();
        java.time.LocalDateTime lastLocationTime = null;
        java.time.LocalDateTime lastDeepfakeTime = null;

        for (NotificationLog log : logs) {
            if (log.getEventType() == NotificationEventType.LOCATION) {
                // 최근 추가된 LOCATION과 5초 이내라면 중복으로 간주하고 스킵
                if (lastLocationTime == null || java.time.Duration.between(log.getSentAt(), lastLocationTime).abs().getSeconds() > 5) {
                    timelineItems.add(TimelineItemResponse.from(log));
                    lastLocationTime = log.getSentAt();
                }
            } else if (log.getEventType() == NotificationEventType.DEEPFAKE) {
                // 최근 추가된 DEEPFAKE와 5초 이내라면 중복으로 간주하고 스킵
                if (lastDeepfakeTime == null || java.time.Duration.between(log.getSentAt(), lastDeepfakeTime).abs().getSeconds() > 5) {
                    timelineItems.add(TimelineItemResponse.from(log));
                    lastDeepfakeTime = log.getSentAt();
                }
            } else {
                // MESSAGE (자동발송) 등은 중복 제거 없이 무조건 보호자 수대로 그대로 추가
                timelineItems.add(TimelineItemResponse.from(log));
            }
        }

        return TimelineListResponse.of(timelineItems);
    }

    /**
     * 공통 로그 조회 메서드 (중복 제거용)
     */
    private List<NotificationLog> getLogsByWardId(Long wardId) {
        if (!userRepository.existsById(wardId)) {
            throw new BusinessException(ErrorCode.WARD_NOT_FOUND);
        }
        return notificationLogRepository.findAllByWardIdOrderBySentAtDesc(wardId);
    }
}
