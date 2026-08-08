package com.ian.community.common.media.dto;

import com.ian.community.common.media.MediaFrame;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MediaEditSourceResponse(
        UUID mediaId,
        String url,
        Instant expiresAt,
        int width,
        int height,
        MediaFrame frame,
        int activeRevision,
        CropRectRequest crop,
        BigDecimal zoom,
        MediaPositionRequest position
) {}
