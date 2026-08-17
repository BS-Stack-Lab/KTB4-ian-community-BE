package com.ian.community.user.follow.service;

import com.ian.community.common.exception.CustomException;
import com.ian.community.common.exception.ErrorCode;
import com.ian.community.user.follow.dto.FollowStateResponse;
import com.ian.community.user.follow.dto.FollowSuccessCode;
import com.ian.community.user.follow.dto.ProfileType;
import com.ian.community.user.follow.repository.UserFollowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class FollowService {
    private final FollowMutationTransactions transactions;
    private final UserFollowRepository userFollowRepository;

    public FollowResult follow(Long followerId, Long followingId) {
        validateDifferentUsers(followerId, followingId);
        boolean created;
        try {
            created = transactions.create(followerId, followingId);
        } catch (DataIntegrityViolationException exception) {
            if (!userFollowRepository
                    .existsByFollowerUserIdAndFollowingUserId(
                            followerId,
                            followingId
                    )) {
                throw exception;
            }
            created = false;
        }
        return new FollowResult(
                created
                        ? FollowSuccessCode.FOLLOW_CREATED
                        : FollowSuccessCode.ALREADY_FOLLOWING,
                new FollowStateResponse(
                        followingId,
                        ProfileType.OTHER_FOLLOWING
                )
        );
    }

    public FollowResult unfollow(Long followerId, Long followingId) {
        validateDifferentUsers(followerId, followingId);
        boolean deleted = transactions.delete(followerId, followingId);
        return new FollowResult(
                deleted
                        ? FollowSuccessCode.FOLLOW_DELETED
                        : FollowSuccessCode.NOT_FOLLOWING,
                new FollowStateResponse(
                        followingId,
                        ProfileType.OTHER_NOT_FOLLOWING
                )
        );
    }

    public void deleteAllForUser(Long userId) {
        transactions.deleteAllForUser(userId);
    }

    private void validateDifferentUsers(Long followerId, Long followingId) {
        if (Objects.equals(followerId, followingId)) {
            throw new CustomException(ErrorCode.SELF_FOLLOW_NOT_ALLOWED);
        }
    }

    public record FollowResult(
            FollowSuccessCode code,
            FollowStateResponse response
    ) {
    }
}
