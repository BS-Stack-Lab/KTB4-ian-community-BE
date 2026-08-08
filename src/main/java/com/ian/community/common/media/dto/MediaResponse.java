package com.ian.community.common.media.dto;

import com.ian.community.common.media.MediaFrame;
import com.ian.community.common.media.MediaStatus;

import java.util.List;
import java.util.UUID;

public record MediaResponse(
        UUID mediaId,
        MediaStatus status,
        MediaFrame frame,
        int mediaRevision,
        int transformVersion,
        String errorCode,
        List<MediaVariantResponse> variants
) {}
