package com.ian.community.user.dto.response;

import com.ian.community.common.media.dto.MediaResponse;

public record ProfileMediaResponse(
        MediaResponse profileMedia,
        String legacyProfileImageUrl
) {}
