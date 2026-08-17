package com.ian.community.user.follow.dto;

public record FollowStateResponse(
        Long targetUserId,
        ProfileType profileType
) {
}
