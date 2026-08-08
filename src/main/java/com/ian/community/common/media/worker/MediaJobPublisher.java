package com.ian.community.common.media.worker;

import java.util.UUID;

public interface MediaJobPublisher {
    void publishRevision(UUID mediaId, int revision);
}
