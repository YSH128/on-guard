package com.shutter.api.gps.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shutter.api.global.exception.BusinessException;
import com.shutter.api.global.exception.ErrorCode;
import com.shutter.api.gps.dto.request.GpsRequest;
import com.shutter.api.gps.dto.response.GpsLocationResponse;
import com.shutter.api.gps.dto.response.GpsResponse;
import com.shutter.api.user.domain.Role;
import com.shutter.api.user.domain.User;
import com.shutter.api.user.repository.UserRelationshipRepository;
import com.shutter.api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
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

    private static final String GPS_KEY_PREFIX = "location:";
    private static final long GPS_TTL_SECONDS = 1800; // 30분

    /**
     * 피보호자(WARD)의 GPS 위치 정보를 Redis에 저장
     */
    public GpsResponse saveLocation(Long userId, GpsRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (Role.GUARDIAN.equals(user.getRole())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        }

        LocalDateTime now = LocalDateTime.now();
        String redisKey = GPS_KEY_PREFIX + userId;

        try {
            // Redis Value 구성
            Map<String, Object> locationData = new HashMap<>();
            locationData.put("lat", request.getLat());
            locationData.put("lng", request.getLng());
            locationData.put("updated_at", now);

            String value = objectMapper.writeValueAsString(locationData);

            // Redis 저장 (TTL: 30분)
            redisTemplate.opsForValue().set(redisKey, value, GPS_TTL_SECONDS, TimeUnit.SECONDS);

            log.debug("Saved GPS location for user {}: {}", userId, value);

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
                        .updatedAt(locationData.get("updated_at") != null ? locationData.get("updated_at").toString() : null);
                        
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
}
