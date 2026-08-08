package com.ian.community.common.media.dto;

import com.ian.community.common.media.MediaFrame;
import com.ian.community.common.media.MediaStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record MediaRevisionResponse(
        UUID mediaId,
        int revision,
        MediaStatus status,
        MediaFrame frame,
        CropRectRequest crop,
        BigDecimal zoom,
        MediaPositionRequest position,
        String errorCode,
        List<MediaVariantResponse> variants
) {}
