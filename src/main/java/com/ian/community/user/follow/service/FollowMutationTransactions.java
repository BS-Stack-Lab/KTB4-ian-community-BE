package com.ian.community.user.follow.service;

import com.ian.community.common.exception.CustomException;
import com.ian.community.common.exception.ErrorCode;
import com.ian.community.user.domain.User;
import com.ian.community.user.follow.domain.FollowCountOutbox;
import com.ian.community.user.follow.domain.FollowEventType;
import com.ian.community.user.follow.domain.UserFollow;
import com.ian.community.user.follow.repository.FollowCountOutboxRepository;
import com.ian.community.user.follow.repository.UserFollowRepository;
import com.ian.community.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FollowMutationTransactions {
    private final UserRepository userRepository;
    private final UserFollowRepository userFollowRepository;
    private final FollowCountOutboxRepository outboxRepository;

    @Transactional
    public boolean create(Long followerId, Long followingId) {
        User follower = getActiveUser(followerId);
        User following = getActiveUser(followingId);
        if (userFollowRepository
                .existsByFollowerUserIdAndFollowingUserId(
                        followerId,
                        followingId
                )) {
            return false;
        }
        userFollowRepository.saveAndFlush(new UserFollow(follower, following));
        outboxRepository.save(new FollowCountOutbox(
                FollowEventType.FOLLOW_CREATED,
                followerId,
                followingId
        ));
        return true;
    }

    @Transactional
    public boolean delete(Long followerId, Long followingId) {
        getActiveUser(followerId);
        getActiveUser(followingId);
        UserFollow follow = userFollowRepository
                .findByFollowerUserIdAndFollowingUserId(
                        followerId,
                        followingId
                )
                .orElse(null);
        if (follow == null) {
            return false;
        }
        userFollowRepository.delete(follow);
        outboxRepository.save(new FollowCountOutbox(
                FollowEventType.FOLLOW_DELETED,
                followerId,
                followingId
        ));
        return true;
    }

    @Transactional
    public void deleteAllForUser(Long userId) {
        List<UserFollow> relationships =
                userFollowRepository.findAllByFollowerUserIdOrFollowingUserId(
                        userId,
                        userId
                );
        relationships.forEach(follow -> {
            outboxRepository.save(new FollowCountOutbox(
                    FollowEventType.FOLLOW_DELETED,
                    follow.getFollower().getUserId(),
                    follow.getFollowing().getUserId()
            ));
            userFollowRepository.delete(follow);
        });
    }

    private User getActiveUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (user.isUserDeleted()) {
            throw new CustomException(ErrorCode.USER_ALREADY_DELETED);
        }
        return user;
    }
}
