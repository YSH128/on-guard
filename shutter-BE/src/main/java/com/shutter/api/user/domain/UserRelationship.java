package com.shutter.api.user.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_relationships")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserRelationship {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guardian_id", nullable = false)
    private User guardian;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ward_id", nullable = false)
    private User ward;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public UserRelationship(User guardian, User ward) {
        this.guardian = guardian;
        this.ward = ward;
        this.createdAt = LocalDateTime.now();
    }
}
