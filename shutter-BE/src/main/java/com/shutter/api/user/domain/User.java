package com.shutter.api.user.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 카카오 로그인 유저 엔티티
 *
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "kakao_id", nullable = false, unique = true)
    private Long kakaoId;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 10)
    private Role role;

    @Column(name = "device_uuid", nullable = false, length = 36)
    private String deviceUuid;

    @Column(name = "fcm_token", length = 255)
    private String fcmToken;

    @Column(name = "gps_sharing", nullable = false)
    private Boolean gpsSharing = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public User(Long kakaoId, String name, String deviceUuid, String fcmToken, Boolean gpsSharing) {
        this.kakaoId = kakaoId;
        this.name = name;
        this.deviceUuid = deviceUuid;
        this.fcmToken = fcmToken;
        if (gpsSharing != null) {
            this.gpsSharing = gpsSharing;
        }
        this.role = null;
        this.createdAt = LocalDateTime.now();
    }

    public void assignRole(Role role) {
        this.role = role;
    }

    public void updateDeviceUuid(String deviceUuid) {
        this.deviceUuid = deviceUuid;
    }

    public void updateGpsSharing(Boolean gpsSharing) {
        this.gpsSharing = gpsSharing;
    }
}
