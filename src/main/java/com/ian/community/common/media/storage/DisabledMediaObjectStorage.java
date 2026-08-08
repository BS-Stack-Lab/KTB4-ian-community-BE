package com.ian.community.common.media.storage;

import com.ian.community.common.exception.CustomException;
import com.ian.community.common.exception.ErrorCode;
import com.ian.community.common.media.dto.PresignedPostResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

@Component
@ConditionalOnProperty(name = "app.media.enabled", havingValue = "false", matchIfMissing = true)
public class DisabledMediaObjectStorage implements MediaObjectStorage {
    private CustomException disabled() {
        return new CustomException(ErrorCode.MEDIA_V2_DISABLED);
    }

    @Override
    public PresignedPostResponse createUpload(String key, String contentType, long maximumSize, Duration duration) {
        throw disabled();
    }

    @Override
    public HeadMediaObject head(String key) {
        throw disabled();
    }

    @Override
    public PresignedGetObject createDownload(String key, Duration duration) {
        throw disabled();
    }

    @Override
    public void download(String key, Path destination) {
        throw disabled();
    }

    @Override
    public void putImmutable(String key, Path source, String contentType, String cacheControl) {
        throw disabled();
    }

    @Override
    public void delete(String key) {
        throw disabled();
    }

    @Override
    public void invalidate(List<String> paths) {
        throw disabled();
    }
}
