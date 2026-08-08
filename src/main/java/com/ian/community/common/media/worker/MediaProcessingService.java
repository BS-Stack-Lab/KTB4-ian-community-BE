package com.ian.community.common.media.worker;

import com.ian.community.common.exception.ErrorCode;
import com.ian.community.common.media.MediaAsset;
import com.ian.community.common.media.MediaProperties;
import com.ian.community.common.media.MediaRevision;
import com.ian.community.common.media.storage.MediaObjectStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.runtime", havingValue = "worker")
public class MediaProcessingService {
    private final MediaProcessingTransactions transactions;
    private final MediaObjectStorage objectStorage;
    private final ImageTransformEngine transformEngine;
    private final MediaProperties properties;
    private final MediaMetrics mediaMetrics;

    public void process(UUID mediaId, int revisionNumber) {
        MediaProcessingTransactions.ProcessingClaim claim = transactions.claim(
                mediaId, revisionNumber
        );
        if (claim == null) {
            return;
        }
        MediaAsset asset = claim.asset();
        MediaRevision revision = claim.revision();
        boolean initialRevision = revisionNumber == 1 && asset.getMasterKey() == null;
        Path directory = null;
        try {
            Path root = Path.of(properties.worker().scratchRoot()).toAbsolutePath().normalize();
            Files.createDirectories(root);
            directory = Files.createTempDirectory(root, "media-" + mediaId + "-");
            Path source = directory.resolve("source");
            objectStorage.download(
                    initialRevision ? asset.getSourceKey() : asset.getMasterKey(),
                    source
            );
            TransformedMedia transformed = initialRevision
                    ? transformEngine.transform(asset, source, directory)
                    : transformEngine.transformRevision(revision, source, directory);

            String masterKey = "private/media/" + mediaId + "/master.r"
                    + 1 + ".t" + revision.getTransformVersion() + ".webp";
            if (initialRevision) {
                objectStorage.putImmutable(
                        masterKey,
                        transformed.masterPath(),
                        "image/webp",
                        "private, no-store"
                );
            } else {
                masterKey = asset.getMasterKey();
            }

            List<MediaProcessingTransactions.StoredVariant> stored = new ArrayList<>();
            for (TransformedVariant variant : transformed.variants()) {
                String key = "public/media/" + mediaId + "/" + variant.type().getKeyName()
                        + ".r" + revisionNumber + ".t"
                        + revision.getTransformVersion() + ".webp";
                objectStorage.putImmutable(
                        key,
                        variant.path(),
                        "image/webp",
                        "public, max-age=31536000, immutable"
                );
                stored.add(new MediaProcessingTransactions.StoredVariant(
                        variant.type(), key, variant.fileSize()
                ));
            }
            boolean committed = transactions.complete(
                    mediaId,
                    revisionNumber,
                    masterKey,
                    transformed.masterWidth(),
                    transformed.masterHeight(),
                    stored
            );
            if (!committed) {
                deleteOutputs(asset, revision);
            }
            if (initialRevision) {
                safeDelete(asset.getSourceKey());
            }
        } catch (PermanentMediaProcessingException exception) {
            deleteOutputs(asset, revision);
            transactions.fail(mediaId, revisionNumber, exception.getErrorCode());
            mediaMetrics.recordProcessingFailure();
            if (initialRevision) {
                safeDelete(asset.getSourceKey());
            }
            throw exception;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to prepare media scratch directory", exception);
        } finally {
            deleteRecursively(directory);
        }
    }

    public void failRetryExhausted(UUID mediaId, int revisionNumber) {
        MediaProcessingTransactions.ProcessingClaim claim = transactions.find(
                mediaId, revisionNumber
        );
        if (claim != null) {
            deleteOutputs(claim.asset(), claim.revision());
            if (revisionNumber == 1) {
                safeDelete(claim.asset().getSourceKey());
            }
        }
        transactions.fail(mediaId, revisionNumber, ErrorCode.PROCESSING_RETRY_EXHAUSTED);
        mediaMetrics.recordProcessingFailure();
    }

    private void deleteOutputs(MediaAsset asset, MediaRevision revision) {
        String suffix = ".r" + revision.getRevision()
                + ".t" + revision.getTransformVersion() + ".webp";
        if (revision.getRevision() == 1 && asset.getMasterKey() == null) {
            safeDelete("private/media/" + asset.getMediaId() + "/master" + suffix);
        }
        for (var type : com.ian.community.common.media.MediaVariantType.forFrame(revision.getFrame())) {
            safeDelete("public/media/" + asset.getMediaId() + "/" + type.getKeyName() + suffix);
        }
        try {
            objectStorage.invalidate(List.of("/media/" + asset.getMediaId() + "/*"));
        } catch (RuntimeException ignored) {
            // A cleanup failure is observable through storage metrics and must not hide FAILED state.
        }
    }

    private void safeDelete(String key) {
        try {
            objectStorage.delete(key);
        } catch (RuntimeException ignored) {
            // Lifecycle cleanup remains a fallback if immediate deletion is temporarily unavailable.
        }
    }

    private void deleteRecursively(Path directory) {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        try {
            Files.walkFileTree(directory, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exception) throws IOException {
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
            // The dedicated scratch directory is swept again when the worker starts.
        }
    }
}
