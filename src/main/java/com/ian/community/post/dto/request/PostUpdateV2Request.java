package com.ian.community.post.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import com.ian.community.common.media.dto.MediaRevisionActivationRequest;

import java.util.List;
import java.util.UUID;

public record PostUpdateV2Request(
        @NotBlank String content,
        @Size(max = 5) List<UUID> mediaIds,
        @Size(max = 5) List<@Valid MediaRevisionActivationRequest> revisionActivations
) {
    public PostUpdateV2Request {
        mediaIds = mediaIds == null ? List.of() : List.copyOf(mediaIds);
        revisionActivations = revisionActivations == null
                ? List.of()
                : List.copyOf(revisionActivations);
    }
}
