package com.shutter.api.notification.service;

import com.shutter.api.global.exception.AlertException;
import com.shutter.api.global.exception.BusinessException;
import com.shutter.api.global.exception.ErrorCode;
import com.shutter.api.notification.client.KakaoPushClient;
import com.shutter.api.notification.domain.NotificationLog;
import com.shutter.api.notification.domain.NotificationStatus;
import com.shutter.api.notification.dto.request.AlertRequest;
import com.shutter.api.notification.dto.response.AlertLogResponse;
import com.shutter.api.notification.repository.NotificationLogRepository;
import com.shutter.api.user.domain.User;
import com.shutter.api.user.repository.UserRelationshipRepository;
import com.shutter.api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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
     * 알림 전송 및 로그 저장
     *
     * @param wardId 피보호자 ID (JWT 추출)
     * @param guardianId 보호자 ID (PathVariable)
     * @param request 알림 요청 정보 (이벤트 타입, 발신자 등)
     * @return 저장된 알림 로그 정보
     */
    @Transactional
    public AlertLogResponse sendAlert(Long wardId, Long guardianId, AlertRequest request) {
        // 1. 피보호자 존재 여부 확인
        User ward = userRepository.findById(wardId)
                .orElseThrow(() -> new AlertException(ErrorCode.WARD_NOT_FOUND));

        // 2. 보호자 존재 여부 확인
        User guardian = userRepository.findById(guardianId)
                .orElseThrow(() -> new AlertException(ErrorCode.GUARDIAN_NOT_FOUND));

        // 3. 보호자-피보호자 관계 존재 여부 확인
        if (!userRelationshipRepository.existsByGuardianIdAndWardId(guardianId, wardId)) {
            throw new AlertException(ErrorCode.RELATIONSHIP_NOT_FOUND);
        }

        // 4. 카카오 푸시 발송 (WebClient 호출)
        String pushMessage = String.format("[%s] 피싱 위험 상황이 감지되었습니다.", request.getEventType());
        boolean pushSuccess = kakaoPushClient.sendPush(guardian.getKakaoId(), request.getEventType(), pushMessage);

        // 5. 알림 로그 생성 및 저장
        NotificationLog notificationLog = NotificationLog.builder()
                .ward(ward)
                .guardian(guardian)
                .caller(request.getCaller())
                .eventType(request.getEventType())
                .callStartedAt(request.getCallStartedAt())
                .status(pushSuccess ? NotificationStatus.SUCCESS : NotificationStatus.FAIL)
                .failReason(pushSuccess ? null : "Kakao Push API 호출 실패")
                .build();

        NotificationLog savedLog = notificationLogRepository.save(notificationLog);

        log.info("Alert sent from ward {} to guardian {}. Status: {}", wardId, guardianId, savedLog.getStatus());

        return AlertLogResponse.from(savedLog);
    }

    /**
     * 피보호자의 알림 로그 목록 조회
     *
     * @param wardId 피보호자 ID
     * @return 알림 로그 목록 (최신순)
     */
    @Transactional(readOnly = true)
    public List<AlertLogResponse> getAlertLogs(Long wardId) {
        // 1. 피보호자 존재 여부 확인
        if (!userRepository.existsById(wardId)) {
            throw new BusinessException(ErrorCode.WARD_NOT_FOUND);
        }

        // 2. 알림 로그 조회 (Repo에서 @EntityGraph를 사용하여 JOIN 처리됨)
        List<NotificationLog> logs = notificationLogRepository.findAllByWardIdOrderBySentAtDesc(wardId);

        // 3. DTO 변환 후 반환
        return logs.stream()
                .map(AlertLogResponse::from)
                .collect(Collectors.toList());
    }
}
