package com.shutter.api.gps.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shutter.api.global.exception.BusinessException;
import com.shutter.api.global.exception.ErrorCode;
import com.shutter.api.gps.dto.request.GpsRequest;
import com.shutter.api.gps.dto.request.GpsSharingToggleRequest;
import com.shutter.api.gps.dto.request.GpsSessionRequest;
import com.shutter.api.gps.dto.response.GpsLocationResponse;
import com.shutter.api.gps.dto.response.GpsResponse;
import com.shutter.api.gps.dto.response.GpsSharingToggleResponse;
import com.shutter.api.gps.dto.response.GpsSessionResponse;
import com.shutter.api.gps.dto.response.LocationExpiredDto;
import com.shutter.api.gps.dto.response.LocationSharingOnDto;
import com.shutter.api.gps.dto.response.LocationUpdateDto;
import com.shutter.api.location.service.GeocodingService;
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
    private final GeocodingService geocodingService;

    private static final String GPS_KEY_PREFIX = "location:";
    private static final long GPS_TTL_SECONDS = 60; // 1분

    /** 통화 세션 전용 GPS 키: call_location:{channelName}:{userId} */
    private static final String CALL_GPS_KEY_PREFIX = "call_location:";
    private static final long CALL_GPS_TTL_SECONDS = 60 * 60; // 1시간

    /**
     * 피보호자(WARD)의 GPS 위치 정보를 Redis에 저장
     */
    public GpsResponse saveLocation(Long userId, GpsRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        LocalDateTime now = LocalDateTime.now();
        String redisKey = GPS_KEY_PREFIX + userId;

        try {
            // Redis Value 구성
            Map<String, Object> locationData = new HashMap<>();
            locationData.put("lat", request.getLat());
            locationData.put("lng", request.getLng());
            locationData.put("updated_at", now);

            String value = objectMapper.writeValueAsString(locationData);

            // setIfAbsent(SETNX): 원자적으로 저장 + 최초 여부 판별
            Boolean isFirstGps = redisTemplate.opsForValue().setIfAbsent(redisKey, value, GPS_TTL_SECONDS, TimeUnit.SECONDS);
            if (!Boolean.TRUE.equals(isFirstGps)) {
                // 이미 키 있음 → TTL 갱신
                redisTemplate.opsForValue().set(redisKey, value, GPS_TTL_SECONDS, TimeUnit.SECONDS);
            }

            log.debug("Saved GPS location for user {}: {}", userId, value);

            List<UserRelationship> relationships = userRelationshipRepository.findByWardIdWithGuardian(userId);
            if (!relationships.isEmpty()) {
                // 최초 GPS 전송 시 보호자들에게 FCM 발송
                if (Boolean.TRUE.equals(isFirstGps)) {
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

    /**
     * 통화 연결 시 현재 유저의 GPS를 통화 세션 전용 Redis 키에 저장.
     * 키: call_location:{channelName}:{userId}, TTL: 1시간
     */
    public void saveSessionLocation(Long userId, GpsSessionRequest request) {
        String redisKey = CALL_GPS_KEY_PREFIX + request.getChannelName() + ":" + userId;
        try {
            Map<String, Object> locationData = new HashMap<>();
            locationData.put("lat", request.getLat());
            locationData.put("lng", request.getLng());
            locationData.put("saved_at", LocalDateTime.now().toString());

            String value = objectMapper.writeValueAsString(locationData);
            redisTemplate.opsForValue().set(redisKey, value, CALL_GPS_TTL_SECONDS, TimeUnit.SECONDS);
            log.debug("Saved session GPS for user {} in channel {}", userId, request.getChannelName());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize session GPS data for user {}", userId, e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 딥페이크 감지 시 상대방의 통화 세션 GPS를 조회하여 국가명으로 변환 후 반환.
     * 데이터 없으면 "알 수 없는 위치" 반환 (예외 발생 안 함 — Soft Fail)
     */
    public GpsSessionResponse getSessionOpponentCountry(String channelName, Long opponentKakaoId) {
        User opponent = userRepository.findByKakaoId(opponentKakaoId).orElse(null);
        if (opponent == null) {
            log.warn("getSessionOpponentCountry: opponentKakaoId={} not found in DB", opponentKakaoId);
            return new GpsSessionResponse("알 수 없는 위치");
        }

        String redisKey = CALL_GPS_KEY_PREFIX + channelName + ":" + opponent.getId();
        String jsonValue = redisTemplate.opsForValue().get(redisKey);

        if (jsonValue == null) {
            log.warn("getSessionOpponentCountry: no session GPS found - channel={}, opponentKakaoId={}",
                    channelName, opponentKakaoId);
            return new GpsSessionResponse("알 수 없는 위치");
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> locationData = objectMapper.readValue(jsonValue, Map.class);
            double lat = Double.parseDouble(locationData.get("lat").toString());
            double lng = Double.parseDouble(locationData.get("lng").toString());

            String country = geocodingService.getCountryName(lat, lng);
            log.info("Session GPS resolved: channel={}, opponentKakaoId={} -> country={}",
                    channelName, opponentKakaoId, country);
            return new GpsSessionResponse(country);
        } catch (Exception e) {
            log.error("Failed to parse session GPS - channel={}, opponentKakaoId={}",
                    channelName, opponentKakaoId, e);
            return new GpsSessionResponse("알 수 없는 위치");
        }
    }
}
