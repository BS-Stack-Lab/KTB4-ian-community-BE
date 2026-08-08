package com.ian.community.common.media;

import com.ian.community.common.media.dto.MediaRevisionRequest;
import com.ian.community.common.media.dto.MediaRevisionResponse;
import com.ian.community.common.media.worker.MediaJobPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class MediaRevisionCoordinator {
    private final MediaRevisionService revisionService;
    private final MediaJobPublisher jobPublisher;

    public MediaRevisionResponse create(
            Long userId,
            UUID mediaId,
            MediaRevisionRequest request
    ) {
        MediaRevisionResponse revision = revisionService.create(userId, mediaId, request);
        try {
            jobPublisher.publishRevision(mediaId, revision.revision());
            return revision;
        } catch (RuntimeException exception) {
            revisionService.failPublish(userId, mediaId, revision.revision());
            throw exception;
        }
    }
}
