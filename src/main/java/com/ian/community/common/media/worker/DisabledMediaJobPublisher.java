package com.ian.community.common.media.worker;

import com.ian.community.common.exception.CustomException;
import com.ian.community.common.exception.ErrorCode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConditionalOnProperty(name = "app.media.enabled", havingValue = "false", matchIfMissing = true)
public class DisabledMediaJobPublisher implements MediaJobPublisher {
    @Override
    public void publishRevision(UUID mediaId, int revision) {
        throw new CustomException(ErrorCode.MEDIA_V2_DISABLED);
    }
}
