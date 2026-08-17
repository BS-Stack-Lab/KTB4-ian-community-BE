package com.ian.community.user.follow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Getter
@Entity
@Table(name = "user_follow_counts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserFollowCount {
    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "follower_count", nullable = false)
    private long followerCount;

    @Column(name = "following_count", nullable = false)
    private long followingCount;

    @Column(name = "projection_version", nullable = false)
    private long projectionVersion;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public UserFollowCount(Long userId) {
        this.userId = userId;
        this.followerCount = 0;
        this.followingCount = 0;
        this.projectionVersion = 0;
        this.updatedAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
    }

    public void replace(long followerCount, long followingCount) {
        this.followerCount = Math.max(0, followerCount);
        this.followingCount = Math.max(0, followingCount);
        this.projectionVersion++;
        this.updatedAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
    }
}
