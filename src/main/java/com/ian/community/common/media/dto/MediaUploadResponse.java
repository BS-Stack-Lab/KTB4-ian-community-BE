package com.ian.community.common.media.dto;

import com.ian.community.common.media.MediaStatus;

import java.util.UUID;

public record MediaUploadResponse(
        UUID mediaId,
        MediaStatus status,
        PresignedPostResponse upload
) {}
