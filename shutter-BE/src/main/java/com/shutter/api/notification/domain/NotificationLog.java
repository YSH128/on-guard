package com.shutter.api.notification.domain;

import com.shutter.api.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "notification_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ward_id", nullable = false)
    private User ward;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guardian_id", nullable = false)
    private User guardian;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private NotificationStatus status;

    @Column(name = "fail_reason", length = 500)
    private String failReason;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Builder
    public NotificationLog(User ward, User guardian, NotificationStatus status, String failReason, LocalDateTime sentAt) {
        this.ward = ward;
        this.guardian = guardian;
        this.status = status;
        this.failReason = failReason;
        this.sentAt = sentAt;
    }
}
