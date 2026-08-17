package com.ian.community.user.follow.repository;

import com.ian.community.user.follow.domain.UserFollow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserFollowRepository extends JpaRepository<UserFollow, Long> {
    boolean existsByFollowerUserIdAndFollowingUserId(
            Long followerId,
            Long followingId
    );

    Optional<UserFollow> findByFollowerUserIdAndFollowingUserId(
            Long followerId,
            Long followingId
    );

    List<UserFollow> findAllByFollowerUserIdOrFollowingUserId(
            Long followerId,
            Long followingId
    );

    @Query("""
            select count(follow)
            from UserFollow follow
            where follow.following.userId = :userId
              and follow.follower.userDeleted = false
              and follow.following.userDeleted = false
            """)
    long countActiveFollowers(@Param("userId") Long userId);

    @Query("""
            select count(follow)
            from UserFollow follow
            where follow.follower.userId = :userId
              and follow.follower.userDeleted = false
              and follow.following.userDeleted = false
            """)
    long countActiveFollowing(@Param("userId") Long userId);
}
