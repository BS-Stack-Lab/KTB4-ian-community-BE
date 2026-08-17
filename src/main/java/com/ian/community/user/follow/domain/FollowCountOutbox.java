package com.ian.community.user.follow.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Getter
@Entity
@Table(name = "follow_count_outbox")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FollowCountOutbox {
    @Id
    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 32)
    private FollowEventType eventType;

    @Column(name = "follower_id", nullable = false)
    private Long followerId;

    @Column(name = "following_id", nullable = false)
    private Long followingId;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "available_at", nullable = false)
    private LocalDateTime availableAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public FollowCountOutbox(
            FollowEventType eventType,
            Long followerId,
            Long followingId
    ) {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        this.eventId = UUID.randomUUID();
        this.eventType = eventType;
        this.followerId = followerId;
        this.followingId = followingId;
        this.attemptCount = 0;
        this.availableAt = now;
        this.createdAt = now;
    }

    public void markProcessed() {
        this.processedAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        this.lastError = null;
    }

    public void reschedule(String error) {
        this.attemptCount++;
        long delaySeconds = Math.min(300, 1L << Math.min(attemptCount, 8));
        this.availableAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"))
                .plusSeconds(delaySeconds);
        this.lastError = error == null
                ? null
                : error.substring(0, Math.min(error.length(), 500));
    }
}
