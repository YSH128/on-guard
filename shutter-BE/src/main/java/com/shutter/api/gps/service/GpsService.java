package com.shutter.api.gps.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shutter.api.global.exception.BusinessException;
import com.shutter.api.global.exception.ErrorCode;
import com.shutter.api.gps.dto.request.GpsRequest;
import com.shutter.api.gps.dto.response.GpsResponse;
import com.shutter.api.user.domain.Role;
import com.shutter.api.user.domain.User;
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
}
