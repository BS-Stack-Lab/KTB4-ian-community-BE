package com.ian.community.common.media.storage;

import com.ian.community.common.exception.CustomException;
import com.ian.community.common.exception.ErrorCode;
import com.ian.community.common.media.MediaProperties;
import com.ian.community.common.media.dto.PresignedPostResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.cloudfront.CloudFrontClient;
import software.amazon.awssdk.services.cloudfront.model.CreateInvalidationRequest;
import software.amazon.awssdk.services.cloudfront.model.InvalidationBatch;
import software.amazon.awssdk.services.cloudfront.model.Paths;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
@ConditionalOnProperty(name = "app.media.enabled", havingValue = "true")
public class S3MediaObjectStorage implements MediaObjectStorage {
    private final S3Client s3Client;
    private final S3PresignedPostFactory postFactory;
    private final S3Presigner s3Presigner;
    private final MediaProperties properties;
    private final CloudFrontClient cloudFrontClient;

    public S3MediaObjectStorage(
            S3Client s3Client,
            S3PresignedPostFactory postFactory,
            S3Presigner s3Presigner,
            MediaProperties properties,
            CloudFrontClient cloudFrontClient
    ) {
        this.s3Client = s3Client;
        this.postFactory = postFactory;
        this.s3Presigner = s3Presigner;
        this.properties = properties;
        this.cloudFrontClient = cloudFrontClient;
    }

    @Override
    public PresignedPostResponse createUpload(
            String key,
            String contentType,
            long maximumSize,
            Duration duration
    ) {
        return postFactory.create(key, contentType, maximumSize, duration);
    }

    @Override
    public HeadMediaObject head(String key) {
        try {
            HeadObjectResponse response = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(key)
                    .build());
            return new HeadMediaObject(response.contentLength(), response.contentType());
        } catch (NoSuchKeyException exception) {
            throw new CustomException(ErrorCode.MEDIA_NOT_FOUND);
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                throw new CustomException(ErrorCode.MEDIA_NOT_FOUND);
            }
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public PresignedGetObject createDownload(String key, Duration duration) {
        Instant expiresAt = Instant.now().plus(duration);
        var request = s3Presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(duration)
                .getObjectRequest(GetObjectRequest.builder()
                        .bucket(properties.bucket())
                        .key(key)
                        .responseCacheControl("private, no-store")
                        .build())
                .build());
        return new PresignedGetObject(request.url().toString(), expiresAt);
    }

    @Override
    public void download(String key, Path destination) {
        s3Client.getObject(GetObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .build(), destination);
    }

    @Override
    public void putImmutable(String key, Path source, String contentType, String cacheControl) {
        try {
            s3Client.putObject(PutObjectRequest.builder()
                            .bucket(properties.bucket())
                            .key(key)
                            .contentType(contentType)
                            .cacheControl(cacheControl)
                            .serverSideEncryption(ServerSideEncryption.AES256)
                            .ifNoneMatch("*")
                            .build(),
                    RequestBody.fromFile(source));
        } catch (S3Exception exception) {
            if (exception.statusCode() != 412) {
                throw exception;
            }
            HeadObjectResponse existing = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(key)
                    .build());
            try {
                if (existing.contentLength() != Files.size(source)
                        || !contentType.equals(existing.contentType())
                        || !cacheControl.equals(existing.cacheControl())) {
                    throw new IllegalStateException("Immutable media object does not match deterministic output");
                }
            } catch (java.io.IOException ioException) {
                throw new IllegalStateException("Unable to inspect immutable media output", ioException);
            }
        }
    }

    @Override
    public void delete(String key) {
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .build());
    }

    @Override
    public void invalidate(List<String> paths) {
        if (paths.isEmpty() || properties.distributionId() == null
                || properties.distributionId().isBlank()) {
            return;
        }
        cloudFrontClient.createInvalidation(CreateInvalidationRequest.builder()
                .distributionId(properties.distributionId())
                .invalidationBatch(InvalidationBatch.builder()
                        .callerReference("media-delete-" + Instant.now().toEpochMilli())
                        .paths(Paths.builder()
                                .quantity(paths.size())
                                .items(paths)
                                .build())
                        .build())
                .build());
    }
}
