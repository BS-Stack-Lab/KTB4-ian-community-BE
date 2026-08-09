package com.ian.community.common.media;

import com.ian.community.common.exception.CustomException;
import com.ian.community.common.exception.ErrorCode;
import com.ian.community.common.media.dto.*;
import com.ian.community.common.media.storage.MediaObjectStorage;
import com.ian.community.common.media.storage.PresignedGetObject;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MediaRevisionService {
    private static final Duration EDIT_SOURCE_EXPIRATION = Duration.ofMinutes(10);

    private final MediaAssetRepository mediaAssetRepository;
    private final MediaRevisionRepository mediaRevisionRepository;
    private final MediaVariantRepository mediaVariantRepository;
    private final MediaObjectStorage mediaObjectStorage;
    private final MediaProperties properties;

    @Transactional
    public MediaRevisionResponse create(Long userId, UUID mediaId, MediaRevisionRequest request) {
        MediaAsset asset = findOwnedAssetForUpdate(userId, mediaId);
        if (asset.getStatus() != MediaStatus.READY || asset.getMasterKey() == null) {
            throw new CustomException(ErrorCode.MEDIA_NOT_READY);
        }
        if (asset.getPurpose() != MediaPurpose.POST || request.frame() == MediaFrame.PROFILE) {
            throw new CustomException(ErrorCode.MEDIA_PURPOSE_MISMATCH);
        }
        validateEdit(request);
        int revisionNumber = asset.allocateRevision();
        MediaRevision revision = new MediaRevision(
                asset,
                revisionNumber,
                MediaStatus.UPLOADED,
                request.frame(),
                0,
                request.crop().x(),
                request.crop().y(),
                request.crop().width(),
                request.crop().height(),
                request.zoom(),
                request.position().x(),
                request.position().y(),
                properties.transformVersion()
        );
        mediaRevisionRepository.save(revision);
        return toResponse(revision);
    }

    @Transactional
    public void failPublish(Long userId, UUID mediaId, int revisionNumber) {
        MediaAsset asset = findOwnedAssetForUpdate(userId, mediaId);
        MediaRevision revision = findRevisionForUpdate(asset, revisionNumber);
        revision.markFailed(ErrorCode.INTERNAL_SERVER_ERROR.getCode());
    }

    public MediaRevisionResponse get(Long userId, UUID mediaId, int revisionNumber) {
        MediaAsset asset = findOwnedAsset(userId, mediaId);
        return toResponse(findRevision(asset, revisionNumber));
    }

    public MediaEditSourceResponse editSource(Long userId, UUID mediaId) {
        MediaAsset asset = findOwnedAsset(userId, mediaId);
        if (asset.getStatus() != MediaStatus.READY || asset.getMasterKey() == null
                || asset.getSourceWidth() == null || asset.getSourceHeight() == null) {
            throw new CustomException(ErrorCode.MEDIA_NOT_READY);
        }
        MediaRevision revision = findRevision(asset, asset.getMediaRevision());
        PresignedGetObject source = mediaObjectStorage.createDownload(
                asset.getMasterKey(), EDIT_SOURCE_EXPIRATION
        );
        return new MediaEditSourceResponse(
                mediaId,
                source.url(),
                source.expiresAt(),
                asset.getSourceWidth(),
                asset.getSourceHeight(),
                revision.getFrame(),
                asset.getMediaRevision(),
                crop(revision),
                revision.getZoom(),
                position(revision)
        );
    }

    @Transactional
    public void activate(Long userId, UUID mediaId, int revisionNumber) {
        MediaAsset asset = findOwnedAssetForUpdate(userId, mediaId);
        MediaRevision revision = findRevisionForUpdate(asset, revisionNumber);
        if (revision.getStatus() != MediaStatus.READY) {
            throw new CustomException(ErrorCode.MEDIA_REVISION_NOT_READY);
        }
        asset.activate(revision);
    }

    @Transactional
    public void cancel(Long userId, UUID mediaId, int revisionNumber) {
        MediaAsset asset = findOwnedAssetForUpdate(userId, mediaId);
        if (asset.getMediaRevision() == revisionNumber) {
            throw new CustomException(ErrorCode.MEDIA_REVISION_ACTIVE);
        }
        MediaRevision revision = findRevisionForUpdate(asset, revisionNumber);
        if (revision.getActivatedAt() != null) {
            throw new CustomException(ErrorCode.MEDIA_REVISION_ACTIVE);
        }
        revision.requestDelete();
        deleteRevisionOutputs(asset, revision);
        revision.markDeleted();
    }

    public MediaRevisionResponse toResponse(MediaRevision revision) {
        List<MediaVariant> storedVariants = mediaVariantRepository
                .findAllByMediaAssetMediaIdAndMediaRevisionOrderByWidthAsc(
                        revision.getMediaAsset().getMediaId(), revision.getRevision()
                );
        List<MediaVariantResponse> variants = MediaVariantPolicy
                .responseVariants(revision.getFrame(), storedVariants)
                .stream()
                .map(variant -> new MediaVariantResponse(
                        variant.getVariantType(),
                        publicUrl(variant.getObjectKey()),
                        variant.getWidth(),
                        variant.getHeight(),
                        variant.getMimeType(),
                        variant.getFileSize()
                ))
                .toList();
        return new MediaRevisionResponse(
                revision.getMediaAsset().getMediaId(),
                revision.getRevision(),
                revision.getStatus(),
                revision.getFrame(),
                crop(revision),
                revision.getZoom(),
                position(revision),
                revision.getErrorCode(),
                variants
        );
    }

    @Transactional
    public void cleanupStale() {
        LocalDateTime cutoff = LocalDateTime.now(ZoneOffset.UTC).minusHours(24);
        mediaRevisionRepository
                .findAllByActivatedAtIsNullAndStatusInAndUpdatedAtBefore(
                        List.of(MediaStatus.READY, MediaStatus.FAILED), cutoff
                )
                .forEach(revision -> {
                    MediaAsset asset = revision.getMediaAsset();
                    if (asset.getMediaRevision() == revision.getRevision()) {
                        return;
                    }
                    revision.requestDelete();
                    deleteRevisionOutputs(asset, revision);
                    revision.markDeleted();
                });
    }

    void deleteRevisionOutputs(MediaAsset asset, MediaRevision revision) {
        List<MediaVariant> variants = mediaVariantRepository
                .findAllByMediaAssetMediaIdAndMediaRevisionOrderByWidthAsc(
                        asset.getMediaId(), revision.getRevision()
                );
        variants.forEach(variant -> mediaObjectStorage.delete(variant.getObjectKey()));
        if (!variants.isEmpty()) {
            mediaObjectStorage.invalidate(variants.stream()
                    .map(MediaVariant::getObjectKey)
                    .filter(key -> key.startsWith("public/"))
                    .map(key -> "/" + key.substring("public/".length()))
                    .toList());
        }
        mediaVariantRepository.deleteAllByMediaAssetMediaIdAndMediaRevision(
                asset.getMediaId(), revision.getRevision()
        );
    }

    private MediaAsset findOwnedAsset(Long userId, UUID mediaId) {
        MediaAsset asset = mediaAssetRepository.findById(mediaId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEDIA_NOT_FOUND));
        validateOwner(asset, userId);
        return asset;
    }

    private MediaAsset findOwnedAssetForUpdate(Long userId, UUID mediaId) {
        MediaAsset asset = mediaAssetRepository.findByIdForUpdate(mediaId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEDIA_NOT_FOUND));
        validateOwner(asset, userId);
        return asset;
    }

    private MediaRevision findRevision(MediaAsset asset, int revisionNumber) {
        return mediaRevisionRepository
                .findByMediaAssetMediaIdAndRevision(asset.getMediaId(), revisionNumber)
                .orElseThrow(() -> new CustomException(ErrorCode.MEDIA_REVISION_NOT_FOUND));
    }

    private MediaRevision findRevisionForUpdate(MediaAsset asset, int revisionNumber) {
        return mediaRevisionRepository.findForUpdate(asset.getMediaId(), revisionNumber)
                .orElseThrow(() -> new CustomException(ErrorCode.MEDIA_REVISION_NOT_FOUND));
    }

    private void validateOwner(MediaAsset asset, Long userId) {
        if (!asset.ownedBy(userId)) {
            throw new CustomException(ErrorCode.UPLOAD_NOT_OWNED);
        }
    }

    private void validateEdit(MediaRevisionRequest request) {
        CropRectRequest crop = request.crop();
        if (crop.x().add(crop.width()).compareTo(BigDecimal.ONE) > 0
                || crop.y().add(crop.height()).compareTo(BigDecimal.ONE) > 0) {
            throw new CustomException(ErrorCode.INVALID_CROP_RECT);
        }
        if (request.zoom().compareTo(BigDecimal.ONE) < 0
                || request.zoom().compareTo(BigDecimal.valueOf(3)) > 0
                || request.position().x().compareTo(BigDecimal.ZERO) < 0
                || request.position().x().compareTo(BigDecimal.ONE) > 0
                || request.position().y().compareTo(BigDecimal.ZERO) < 0
                || request.position().y().compareTo(BigDecimal.ONE) > 0) {
            throw new CustomException(ErrorCode.INVALID_CROP_RECT);
        }
    }

    private CropRectRequest crop(MediaRevision revision) {
        return new CropRectRequest(
                revision.getCropX(), revision.getCropY(),
                revision.getCropWidth(), revision.getCropHeight()
        );
    }

    private MediaPositionRequest position(MediaRevision revision) {
        return new MediaPositionRequest(revision.getPositionX(), revision.getPositionY());
    }

    private String publicUrl(String objectKey) {
        String base = properties.cdnBaseUrl() == null
                ? ""
                : properties.cdnBaseUrl().replaceAll("/+$", "");
        String path = objectKey.startsWith("public/")
                ? objectKey.substring("public/".length())
                : objectKey;
        return base + "/" + path;
    }
}
