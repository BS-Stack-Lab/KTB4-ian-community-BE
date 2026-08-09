package com.ian.community.common.media.worker;

import com.ian.community.common.exception.ErrorCode;
import com.ian.community.common.media.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class MediaProcessingTransactions {
    private final MediaAssetRepository mediaAssetRepository;
    private final MediaVariantRepository mediaVariantRepository;
    private final MediaRevisionRepository mediaRevisionRepository;

    @Transactional(readOnly = true)
    public ProcessingClaim find(UUID mediaId, int revisionNumber) {
        MediaAsset asset = mediaAssetRepository.findById(mediaId).orElse(null);
        MediaRevision revision = mediaRevisionRepository
                .findByMediaAssetMediaIdAndRevision(mediaId, revisionNumber)
                .orElse(null);
        return asset == null || revision == null ? null : new ProcessingClaim(asset, revision);
    }

    @Transactional
    public ProcessingClaim claim(UUID mediaId, int revisionNumber) {
        MediaAsset asset = mediaAssetRepository.findByIdForUpdate(mediaId).orElse(null);
        if (asset == null) {
            return null;
        }
        MediaRevision revision = mediaRevisionRepository
                .findForUpdate(mediaId, revisionNumber)
                .orElse(null);
        if (revision == null) {
            return null;
        }
        if (revision.getStatus() == MediaStatus.PENDING_UPLOAD) {
            throw new MediaNotReadyForProcessingException();
        }
        try {
            if (!revision.claim(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(5))) {
                return null;
            }
        } catch (IllegalStateException exception) {
            throw new MediaNotReadyForProcessingException();
        }
        if (revisionNumber == 1
                && !asset.claim(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(5))) {
            return null;
        }
        return new ProcessingClaim(asset, revision);
    }

    @Transactional
    public boolean complete(
            UUID mediaId,
            int revisionNumber,
            String masterKey,
            int width,
            int height,
            List<StoredVariant> variants
    ) {
        MediaAsset asset = mediaAssetRepository.findByIdForUpdate(mediaId).orElseThrow();
        MediaRevision revision = mediaRevisionRepository.findForUpdate(mediaId, revisionNumber)
                .orElseThrow();
        if (revision.getStatus() == MediaStatus.READY) {
            return true;
        }
        if (revision.getStatus() != MediaStatus.PROCESSING) {
            return false;
        }
        mediaVariantRepository.deleteAllByMediaAssetMediaIdAndMediaRevision(
                mediaId, revisionNumber
        );
        variants.forEach(variant -> mediaVariantRepository.save(new MediaVariant(
                asset, revision, variant.type(), variant.objectKey(), variant.fileSize()
        )));
        revision.markReady();
        if (revisionNumber == 1) {
            asset.markReady(masterKey, width, height);
            revision.markActivated();
        }
        return true;
    }

    @Transactional
    public void fail(UUID mediaId, int revisionNumber, ErrorCode errorCode) {
        mediaRevisionRepository.findForUpdate(mediaId, revisionNumber)
                .ifPresent(revision -> revision.markFailed(errorCode.getCode()));
        if (revisionNumber == 1) {
            mediaAssetRepository.findByIdForUpdate(mediaId)
                    .ifPresent(asset -> asset.markFailed(errorCode.getCode()));
        }
    }

    @Transactional
    public void releaseForRetry(UUID mediaId, int revisionNumber) {
        mediaRevisionRepository.findForUpdate(mediaId, revisionNumber)
                .ifPresent(MediaRevision::releaseForRetry);
        if (revisionNumber == 1) {
            mediaAssetRepository.findByIdForUpdate(mediaId)
                    .ifPresent(MediaAsset::releaseForRetry);
        }
    }

    public record ProcessingClaim(MediaAsset asset, MediaRevision revision) {}

    public record StoredVariant(MediaVariantType type, String objectKey, long fileSize) {}
}
