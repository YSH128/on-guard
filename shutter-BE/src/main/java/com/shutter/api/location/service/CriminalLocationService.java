package com.shutter.api.location.service;

import com.shutter.api.global.exception.BusinessException;
import com.shutter.api.global.exception.ErrorCode;
import com.shutter.api.location.dto.request.CriminalLocationReportDto;
import com.shutter.api.location.dto.request.CriminalLocationRequestDto;
import com.shutter.api.notification.client.KakaoPushClient;
import com.shutter.api.notification.domain.PushResult;
import com.shutter.api.user.domain.User;
import com.shutter.api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CriminalLocationService {

    private final UserRepository userRepository;
    private final KakaoPushClient kakaoPushClient;

    /**
     * 딥페이크/딥보이스 감지 시 상대방에게 사일런트 FCM 전송
     * 상대방 앱이 백그라운드에서 GPS를 읽어 서버로 보고하도록 트리거
     */
    public void requestCriminalLocation(Long requesterId, CriminalLocationRequestDto dto) {
        User requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        User opponent = userRepository.findByKakaoId(dto.getOpponentKakaoId())
                .orElseThrow(() -> new BusinessException(ErrorCode.OPPONENT_NOT_FOUND));

        Map<String, String> data = Map.of(
                "type", "location_request",
                "requesterKakaoId", String.valueOf(requester.getKakaoId())
        );

        PushResult result = kakaoPushClient.sendDataPush(opponent.getKakaoId(), data);
        if (result == PushResult.CONFIG_ERROR) {
            log.error("CRITICAL: Kakao Push configuration error while sending location_request.");
            throw new BusinessException(ErrorCode.KAKAO_PUSH_CONFIG_ERROR);
        }
        if (result != PushResult.SUCCESS) {
            log.warn("Failed to send location_request push to kakaoId: {}", opponent.getKakaoId());
            throw new BusinessException(ErrorCode.FCM_SEND_FAILED);
        }

        log.info("location_request sent: requester={} -> opponent kakaoId={}", requesterId, opponent.getKakaoId());
    }

    /**
     * 상대방 앱이 GPS를 읽은 뒤 서버로 위치 보고
     * 요청자에게 위치 데이터를 포함한 FCM 전송
     */
    public void reportCriminalLocation(Long reporterId, CriminalLocationReportDto dto) {
        // 보고자 존재 확인 (JWT 검증 이미 됐지만 안전하게 재확인)
        userRepository.findById(reporterId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        User requester = userRepository.findByKakaoId(dto.getRequesterKakaoId())
                .orElseThrow(() -> new BusinessException(ErrorCode.OPPONENT_NOT_FOUND));

        Map<String, String> data = Map.of(
                "type", "criminal_location",
                "lat", String.valueOf(dto.getLat()),
                "lng", String.valueOf(dto.getLng())
        );

        PushResult result = kakaoPushClient.sendDataPush(requester.getKakaoId(), data);
        if (result == PushResult.CONFIG_ERROR) {
            log.error("CRITICAL: Kakao Push configuration error while sending criminal_location.");
            throw new BusinessException(ErrorCode.KAKAO_PUSH_CONFIG_ERROR);
        }
        if (result != PushResult.SUCCESS) {
            log.warn("Failed to send criminal_location push to kakaoId: {}", requester.getKakaoId());
            throw new BusinessException(ErrorCode.FCM_SEND_FAILED);
        }

        log.info("criminal_location sent: reporter={} -> requester kakaoId={}, lat={}, lng={}",
                reporterId, requester.getKakaoId(), dto.getLat(), dto.getLng());
    }
}