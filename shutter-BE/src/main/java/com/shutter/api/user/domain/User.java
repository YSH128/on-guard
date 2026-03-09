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
    private Integer id;

    @Column(name = "kakao_id", nullable = false, unique = true)
    private String kakaoId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Builder
    public User(String kakaoId) {
        this.kakaoId = kakaoId;
        this.createdAt = LocalDateTime.now();
    }
}
