package com.ian.community.post.dto.response;

import com.ian.community.common.media.dto.MediaResponse;
import com.ian.community.user.domain.User;

public record AuthorV2Response(
        Long userId,
        String nickname,
        MediaResponse profileMedia,
        String legacyProfileImageUrl
) {
    public static AuthorV2Response from(User user, MediaResponse profileMedia) {
        return new AuthorV2Response(
                user.getUserId(),
                user.getNickname(),
                profileMedia,
                profileMedia == null ? user.getProfileImage() : null
        );
    }
}
