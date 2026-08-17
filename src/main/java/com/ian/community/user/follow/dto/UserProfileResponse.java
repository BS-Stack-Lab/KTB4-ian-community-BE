package com.ian.community.user.follow.dto;

import com.ian.community.common.media.dto.MediaResponse;

import java.time.LocalDateTime;

public record UserProfileResponse(
        Long userId,
        String nickname,
        MediaResponse profileMedia,
        String legacyProfileImageUrl,
        long followerCount,
        long followingCount,
        LocalDateTime countUpdatedAt,
        ProfileType profileType
) {
}
