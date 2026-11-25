package com.shutter.api.gps.bench;

import com.shutter.api.global.exception.BusinessException;
import com.shutter.api.global.exception.ErrorCode;
import com.shutter.api.gps.dto.request.GpsRequest;
import com.shutter.api.gps.dto.response.GpsLocationResponse;
import com.shutter.api.gps.dto.response.GpsResponse;
import com.shutter.api.gps.dto.response.LocationUpdateDto;
import com.shutter.api.notification.client.KakaoPushClient;
import com.shutter.api.notification.domain.NotificationEventType;
import com.shutter.api.user.domain.Role;
import com.shutter.api.user.domain.User;
import com.shutter.api.user.domain.UserRelationship;
import com.shutter.api.user.repository.UserRelationshipRepository;
import com.shutter.api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 벤치마크 비교군: Redis 없이 MySQL 직접 쓰기/조회로 GPS 위치를 처리하는 서비스
 * (bench 프로필에서만 활성화)
 */
@Slf4j
@Service
@Profile("bench")
@RequiredArgsConstructor
public class GpsDbBenchService {

    private final UserRepository userRepository;
    private final UserRelationshipRepository userRelationshipRepository;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final KakaoPushClient kakaoPushClient;

    private static final long GPS_TTL_SECONDS = 60;

    private static final String UPSERT_SQL = """
            INSERT INTO ward_locations (ward_id, lat, lng, updated_at, is_sharing)
            VALUES (?, ?, ?, ?, TRUE)
            ON DUPLICATE KEY UPDATE
                lat = VALUES(lat),
                lng = VALUES(lng),
                updated_at = VALUES(updated_at),
                is_sharing = TRUE
            """;

    private static final String SELECT_SQL = """
            SELECT lat, lng, updated_at, is_sharing
            FROM ward_locations
            WHERE ward_id = ? AND is_sharing = TRUE AND updated_at >= ?
            """;

    /**
     * 피보호자(WARD)의 GPS 위치를 MySQL에 트랜잭션 커밋으로 저장한 뒤 WebSocket으로 전송
     */
    public GpsResponse saveLocationDb(Long userId, GpsRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (user.getRole() != Role.WARD) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        }

        if (!Boolean.TRUE.equals(user.getGpsSharing())) {
            throw new BusinessException(ErrorCode.GPS_SHARING_DISABLED);
        }

        LocalDateTime now = LocalDateTime.now();
        Timestamp ts = Timestamp.valueOf(now);

        // 트랜잭션 커밋 완료 후 WebSocket 푸시 (커밋 전 조회 시 구 데이터 반환 방지)
        Integer affectedRows = transactionTemplate.execute(status ->
                jdbcTemplate.update(UPSERT_SQL, userId, request.getLat(), request.getLng(), ts)
        );
        boolean isFirstGps = affectedRows != null && affectedRows == 1;

        List<UserRelationship> relationships = userRelationshipRepository.findByWardIdWithGuardian(userId);
        if (!relationships.isEmpty()) {
            if (isFirstGps) {
                String pushMessage = NotificationEventType.LOCATION.getPushMessage(user.getName());
                for (UserRelationship rel : relationships) {
                    try {
                        kakaoPushClient.sendPush(rel.getGuardian().getKakaoId(), user.getName(),
                                NotificationEventType.LOCATION, pushMessage);
                    } catch (Exception e) {
                        log.warn("Failed to send LOCATION push to guardian KakaoId: {}", rel.getGuardian().getKakaoId(), e);
                    }
                }
            }

            LocationUpdateDto updateDto = LocationUpdateDto.builder()
                    .wardId(userId)
                    .name(user.getName())
                    .lat(request.getLat())
                    .lng(request.getLng())
                    .updatedAt(now.toString())
                    .isSharing(true)
                    .build();

            messagingTemplate.convertAndSend("/topic/location/" + userId, updateDto);
        }

        return new GpsResponse(now);
    }

    /**
     * 보호자가 MySQL에서 직접 피보호자 최신 위치를 조회 (폴링 비교용)
     */
    public GpsLocationResponse getLocationDb(Long guardianId, Long wardId) {
        boolean hasRelationship = userRelationshipRepository.existsByGuardianIdAndWardId(guardianId, wardId);
        if (!hasRelationship) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        }

        User ward = userRepository.findById(wardId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Timestamp cutoff = Timestamp.valueOf(LocalDateTime.now().minusSeconds(GPS_TTL_SECONDS));
        List<GpsLocationResponse> rows = jdbcTemplate.query(
                SELECT_SQL,
                (rs, rowNum) -> GpsLocationResponse.builder()
                        .wardId(wardId)
                        .name(ward.getName())
                        .isSharing(true)
                        .lat(rs.getDouble("lat"))
                        .lng(rs.getDouble("lng"))
                        .updatedAt(rs.getTimestamp("updated_at").toLocalDateTime().toString())
                        .build(),
                wardId, cutoff
        );

        if (rows.isEmpty()) {
            return GpsLocationResponse.builder()
                    .wardId(wardId)
                    .name(ward.getName())
                    .isSharing(false)
                    .build();
        }
        return rows.get(0);
    }
}
