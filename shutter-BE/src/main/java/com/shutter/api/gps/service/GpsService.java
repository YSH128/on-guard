package com.shutter.api.gps.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shutter.api.global.exception.BusinessException;
import com.shutter.api.global.exception.ErrorCode;
import com.shutter.api.gps.dto.request.GpsRequest;
import com.shutter.api.gps.dto.request.GpsSharingToggleRequest;
import com.shutter.api.gps.dto.response.GpsLocationResponse;
import com.shutter.api.gps.dto.response.GpsResponse;
import com.shutter.api.gps.dto.response.GpsSharingToggleResponse;
import com.shutter.api.gps.dto.response.LocationExpiredDto;
import com.shutter.api.gps.dto.response.LocationSharingOnDto;
import com.shutter.api.gps.dto.response.LocationUpdateDto;
import com.shutter.api.notification.client.KakaoPushClient;
import com.shutter.api.notification.domain.NotificationEventType;
import com.shutter.api.user.domain.User;
import com.shutter.api.user.domain.UserRelationship;
import com.shutter.api.user.repository.UserRelationshipRepository;
import com.shutter.api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class GpsService {

    private final UserRepository userRepository;
    private final UserRelationshipRepository userRelationshipRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final KakaoPushClient kakaoPushClient;

    private static final String GPS_KEY_PREFIX = "location:";
    private static final long GPS_TTL_SECONDS = 60; // 1분

    /**
     * 피보호자(WARD)의 GPS 위치 정보를 Redis에 저장
     */
    public GpsResponse saveLocation(Long userId, GpsRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        LocalDateTime now = LocalDateTime.now();
        String redisKey = GPS_KEY_PREFIX + userId;

        // 최초 GPS 전송 여부 확인 (Redis 키가 없으면 첫 전송)
        boolean isFirstGps = !Boolean.TRUE.equals(redisTemplate.hasKey(redisKey));

        try {
            // Redis Value 구성
            Map<String, Object> locationData = new HashMap<>();
            locationData.put("lat", request.getLat());
            locationData.put("lng", request.getLng());
            locationData.put("updated_at", now);

            String value = objectMapper.writeValueAsString(locationData);

            // Redis 저장 (TTL 적용)
            redisTemplate.opsForValue().set(redisKey, value, GPS_TTL_SECONDS, TimeUnit.SECONDS);

            log.debug("Saved GPS location for user {}: {}", userId, value);

            List<UserRelationship> relationships = userRelationshipRepository.findByWardIdWithGuardian(userId);
            if (!relationships.isEmpty()) {
                // 최초 GPS 전송 시 보호자들에게 FCM 발송
                if (isFirstGps) {
                    String pushMessage = NotificationEventType.LOCATION.getPushMessage(user.getName());
                    for (UserRelationship rel : relationships) {
                        kakaoPushClient.sendPush(rel.getGuardian().getKakaoId(), user.getName(),
                                NotificationEventType.LOCATION, pushMessage);
                    }
                    log.info("Sent LOCATION FCM to {} guardians for ward {}", relationships.size(), userId);
                }

                // WebSocket 푸시 전송 (구독 중인 채널로)
                LocationUpdateDto updateDto = LocationUpdateDto.builder()
                        .wardId(userId)
                        .name(user.getName())
                        .lat(request.getLat())
                        .lng(request.getLng())
                        .updatedAt(now.toString())
                        .isSharing(true)
                        .build();

                String destination = "/topic/location/" + userId;
                messagingTemplate.convertAndSend(destination, updateDto);
                log.debug("Published LOCATION_UPDATE to {}", destination);
            }

            return new GpsResponse(now);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize GPS data", e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 보호자가 피보호자의 위치를 조회
     */
    public GpsLocationResponse getLocation(Long guardianId, Long wardId) {
        // 1. 권한 검증: guardianId가 wardId의 보호자가 맞는지 확인
        boolean hasRelationship = userRelationshipRepository.existsByGuardianIdAndWardId(guardianId, wardId);
        if (!hasRelationship) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        }

        // 2. 피보호자 정보 조회 (이름을 얻기 위함)
        User ward = userRepository.findById(wardId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 3. Redis에서 위치 데이터 조회
        String redisKey = GPS_KEY_PREFIX + wardId;
        String jsonValue = redisTemplate.opsForValue().get(redisKey);

        GpsLocationResponse.GpsLocationResponseBuilder responseBuilder = GpsLocationResponse.builder()
                .wardId(wardId)
                .name(ward.getName());

        if (jsonValue != null) {
            try {
                Map<String, Object> locationData = objectMapper.readValue(jsonValue, Map.class);

                responseBuilder.isSharing(true)
                        .lat(Double.valueOf(locationData.get("lat").toString()))
                        .lng(Double.valueOf(locationData.get("lng").toString()))
                        .updatedAt(locationData.get("updated_at") != null ? locationData.get("updated_at").toString()
                                : null);

            } catch (Exception e) {
                log.error("Failed to parse GPS data from Redis for user {}", wardId, e);
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
            }
        } else {
            // Redis에 키가 없으면 공유 중지(또는 만료) 상태
            responseBuilder.isSharing(false);
        }

        return responseBuilder.build();
    }

    /**
     * 피보호자(WARD)의 GPS 공유 상태 토글
     */
    @Transactional
    public GpsSharingToggleResponse toggleSharing(Long userId, GpsSharingToggleRequest request) {
        // 1. 유저 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 2. 권한 검증: 모든 유저가 자신의 위치 공유를 토글할 수 있음

        // 3. DB 공유 상태 업데이트 (Dirty Checking)
        user.updateGpsSharing(request.getGpsSharing());

        // 4. 공유 중지 시 Redis 잔여 데이터 즉시 삭제
        if (Boolean.FALSE.equals(request.getGpsSharing())) {
            String redisKey = GPS_KEY_PREFIX + userId;
            redisTemplate.delete(redisKey);
            log.debug("Deleted GPS location from Redis for user {} due to sharing toggle OFF", userId);

            // LOCATION_EXPIRED 이벤트 발송
            LocationExpiredDto expiredDto = LocationExpiredDto.builder()
                    .wardId(userId)
                    .isSharing(false)
                    .build();

            String destination = "/topic/location/" + userId;
            messagingTemplate.convertAndSend(destination, expiredDto);
            log.debug("Published LOCATION_EXPIRED to {} on toggle OFF", destination);

        } else {
            // 5. 공유 ON 재전환 시 보호자들에게 LOCATION_SHARING_ON 이벤트 발송
            List<UserRelationship> relationships = userRelationshipRepository.findByWardIdWithGuardian(userId);
            if (!relationships.isEmpty()) {
                LocationSharingOnDto sharingOnDto = LocationSharingOnDto.builder()
                        .wardId(userId)
                        .build();

                String destination = "/topic/location/" + userId;
                messagingTemplate.convertAndSend(destination, sharingOnDto);
                log.debug("Published LOCATION_SHARING_ON to {} on toggle ON", destination);
            }
        }

        return new GpsSharingToggleResponse(user.getGpsSharing());
    }
}
