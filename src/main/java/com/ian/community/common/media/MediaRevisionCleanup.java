package com.ian.community.common.media;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.runtime", havingValue = "api", matchIfMissing = true)
public class MediaRevisionCleanup {
    private final MediaRevisionService revisionService;

    @Scheduled(fixedDelayString = "PT1H")
    public void cleanup() {
        revisionService.cleanupStale();
    }
}
