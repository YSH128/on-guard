package com.shutter.api.notification.domain;

import com.shutter.api.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

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
    @Column(name = "event_type", nullable = false, length = 20)
    private NotificationEventType eventType;

    @Column(name = "caller", length = 50)
    private String caller;

    @Column(name = "call_started_at")
    private LocalDateTime callStartedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private NotificationStatus status;

    @Column(name = "fail_reason", length = 500)
    private String failReason;

    @CreationTimestamp
    @Column(name = "sent_at", nullable = false, updatable = false)
    private LocalDateTime sentAt;

    @Builder
    public NotificationLog(User ward, User guardian, String caller, NotificationEventType eventType,
            LocalDateTime callStartedAt, NotificationStatus status, String failReason) {
        this.ward = ward;
        this.guardian = guardian;
        this.caller = caller;
        this.eventType = eventType;
        this.callStartedAt = callStartedAt;
        this.status = status;
        this.failReason = failReason;
    }
}
