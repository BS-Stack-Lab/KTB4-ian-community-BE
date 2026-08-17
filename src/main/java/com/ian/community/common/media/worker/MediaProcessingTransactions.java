package com.ian.community.common.media.worker;

import com.ian.community.common.exception.ErrorCode;
import com.ian.community.common.media.*;
import com.ian.community.post.domain.PostImage;
import com.ian.community.post.repository.PostImageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
    private final PostImageRepository postImageRepository;
    private final MediaProperties properties;

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
            String sourceFormat,
            String outputContentType,
            int cropPixelWidth,
            int cropPixelHeight,
            MediaQualityLevel qualityLevel,
            BigDecimal upscaleRatio1x,
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
                asset,
                revision,
                variant.type(),
                variant.objectKey(),
                variant.mimeType(),
                variant.fileSize()
        )));
        revision.markReady(
                cropPixelWidth,
                cropPixelHeight,
                qualityLevel,
                upscaleRatio1x
        );
        if (revisionNumber == 1) {
            asset.markReady(masterKey, width, height, sourceFormat, outputContentType);
            revision.markActivated();
        }
        List<PostImage> pending = postImageRepository
                .findAllByPendingMediaMediaIdAndPendingRevisionAndMediaOperationId(
                        mediaId, revisionNumber, revision.getOperationId()
                );
        if (!pending.isEmpty() && revisionNumber > 1) {
            asset.activate(revision);
        }
        if (!pending.isEmpty()) {
            String url = compatibilityUrl(revision, variants);
            pending.forEach(image -> image.promotePending(url));
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
        postImageRepository
                .findAllByPendingMediaMediaIdAndPendingRevisionAndMediaOperationId(
                        mediaId, revisionNumber,
                        mediaRevisionRepository
                                .findByMediaAssetMediaIdAndRevision(mediaId, revisionNumber)
                                .map(MediaRevision::getOperationId)
                                .orElse(new UUID(0, 0))
                )
                .forEach(image -> image.failPending(errorCode.getCode()));
    }

    private String compatibilityUrl(MediaRevision revision, List<StoredVariant> variants) {
        MediaVariantType preferred = revision.getMediaAsset().getPurpose() == MediaPurpose.PROFILE
                ? MediaVariantType.PROFILE_MEDIUM
                : revision.getFrame() == MediaFrame.POST_PORTRAIT
                ? MediaVariantType.POST_PORTRAIT_1X
                : MediaVariantType.POST_LANDSCAPE_1X;
        String key = variants.stream()
                .filter(variant -> variant.type() == preferred)
                .findFirst()
                .orElseGet(variants::getFirst)
                .objectKey();
        String base = properties.cdnBaseUrl() == null
                ? ""
                : properties.cdnBaseUrl().replaceAll("/+$", "");
        String path = key.startsWith("public/")
                ? key.substring("public/".length())
                : key;
        return base + "/" + path;
    }

    public record ProcessingClaim(MediaAsset asset, MediaRevision revision) {}

    public record StoredVariant(
            MediaVariantType type,
            String objectKey,
            String mimeType,
            long fileSize
    ) {}
}
