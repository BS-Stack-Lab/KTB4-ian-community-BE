package com.ian.community.common.media.dto;

import java.time.Instant;
import java.util.Map;

public record PresignedPostResponse(
        String url,
        Map<String, String> fields,
        Instant expiresAt
) {}
