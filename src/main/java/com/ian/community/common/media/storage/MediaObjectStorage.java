package com.ian.community.common.media.storage;

import com.ian.community.common.media.dto.PresignedPostResponse;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

public interface MediaObjectStorage {
    PresignedPostResponse createUpload(
            String key,
            String contentType,
            long maximumSize,
            Duration duration
    );

    HeadMediaObject head(String key);

    PresignedGetObject createDownload(String key, Duration duration);

    void download(String key, Path destination);

    void putImmutable(String key, Path source, String contentType, String cacheControl);

    void delete(String key);

    void invalidate(List<String> paths);
}
