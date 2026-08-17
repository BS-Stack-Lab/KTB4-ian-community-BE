package com.ian.community.post.dto.request;

import com.ian.community.common.media.dto.MediaRevisionActivationRequest;
import com.ian.community.common.media.dto.MediaRevisionTargetRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record PostUpdateV2Request(
        @NotBlank String content,
        @Size(max = 5) List<UUID> mediaIds,
        @Size(max = 5) List<@Valid MediaRevisionActivationRequest> revisionActivations,
        @Size(max = 5) List<@Valid MediaRevisionTargetRequest> revisionTargets
) {
    public PostUpdateV2Request {
        mediaIds = mediaIds == null ? List.of() : List.copyOf(mediaIds);
        revisionActivations = revisionActivations == null
                ? List.of()
                : List.copyOf(revisionActivations);
        revisionTargets = revisionTargets == null ? List.of() : List.copyOf(revisionTargets);
    }
}
