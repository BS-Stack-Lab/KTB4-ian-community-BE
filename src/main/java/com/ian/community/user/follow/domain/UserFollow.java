package com.ian.community.user.follow.domain;

import com.ian.community.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Getter
@Entity
@Table(
        name = "user_follows",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_follows_follower_following",
                columnNames = {"follower_id", "following_id"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserFollow {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "follow_id", nullable = false)
    private Long followId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "follower_id", nullable = false)
    private User follower;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "following_id", nullable = false)
    private User following;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public UserFollow(User follower, User following) {
        if (follower.getUserId().equals(following.getUserId())) {
            throw new IllegalArgumentException("본인 팔로우 관계는 생성할 수 없습니다.");
        }
        this.follower = follower;
        this.following = following;
        this.createdAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
    }
}
