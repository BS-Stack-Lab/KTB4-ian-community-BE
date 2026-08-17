package com.ian.community.user.follow.service;

import com.ian.community.common.exception.CustomException;
import com.ian.community.common.exception.ErrorCode;
import com.ian.community.common.media.MediaService;
import com.ian.community.common.media.dto.MediaResponse;
import com.ian.community.user.domain.User;
import com.ian.community.user.follow.domain.UserFollowCount;
import com.ian.community.user.follow.dto.ProfileType;
import com.ian.community.user.follow.dto.UserProfileResponse;
import com.ian.community.user.follow.repository.UserFollowCountRepository;
import com.ian.community.user.follow.repository.UserFollowRepository;
import com.ian.community.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserProfileService {
    private final UserRepository userRepository;
    private final UserFollowRepository userFollowRepository;
    private final UserFollowCountRepository userFollowCountRepository;
    private final MediaService mediaService;

    public UserProfileResponse getProfile(Long viewerUserId, Long profileUserId) {
        User profileUser = getActiveUser(profileUserId);
        getActiveUser(viewerUserId);
        UserFollowCount count = userFollowCountRepository.findById(profileUserId)
                .orElse(null);
        ProfileType profileType;
        if (Objects.equals(viewerUserId, profileUserId)) {
            profileType = ProfileType.SELF;
        } else if (userFollowRepository
                .existsByFollowerUserIdAndFollowingUserId(
                        viewerUserId,
                        profileUserId
                )) {
            profileType = ProfileType.OTHER_FOLLOWING;
        } else {
            profileType = ProfileType.OTHER_NOT_FOLLOWING;
        }
        MediaResponse profileMedia = profileUser.getProfileMedia() == null
                ? null
                : mediaService.toResponse(profileUser.getProfileMedia());
        return new UserProfileResponse(
                profileUser.getUserId(),
                profileUser.getNickname(),
                profileMedia,
                profileMedia == null ? profileUser.getProfileImage() : null,
                count == null ? 0 : count.getFollowerCount(),
                count == null ? 0 : count.getFollowingCount(),
                count == null ? null : count.getUpdatedAt(),
                profileType
        );
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
