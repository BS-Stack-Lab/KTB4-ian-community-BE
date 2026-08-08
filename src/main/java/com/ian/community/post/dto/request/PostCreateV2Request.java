package com.ian.community.post.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record PostCreateV2Request(
        @NotBlank String content,
        @Size(max = 5) List<UUID> mediaIds
) {
    public PostCreateV2Request {
        mediaIds = mediaIds == null ? List.of() : List.copyOf(mediaIds);
    }
}
