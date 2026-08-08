package com.ian.community.common.media.dto;

import com.ian.community.common.media.MediaVariantType;

public record MediaVariantResponse(
        MediaVariantType type,
        String url,
        int width,
        int height,
        String mimeType,
        long fileSize
) {}
