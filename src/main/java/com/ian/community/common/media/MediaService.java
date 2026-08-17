package com.ian.community.common.media;

import com.ian.community.common.exception.CustomException;
import com.ian.community.common.exception.ErrorCode;
import com.ian.community.common.media.dto.*;
import com.ian.community.common.media.storage.HeadMediaObject;
import com.ian.community.common.media.storage.MediaObjectStorage;
import com.ian.community.post.repository.PostImageRepository;
import com.ian.community.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MediaService {
    private static final long PROFILE_MAX_SIZE = 1024L * 1024L;
    private static final long POST_MAX_SIZE = 10L * 1024L * 1024L;
    private static final Set<String> CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/bmp"
    );
    private static final Map<String, Set<String>> EXTENSIONS = Map.of(
            "image/jpeg", Set.of("jpg", "jpeg"),
            "image/png", Set.of("png"),
            "image/webp", Set.of("webp"),
            "image/bmp", Set.of("bmp")
    );

    private final MediaAssetRepository mediaAssetRepository;
    private final MediaVariantRepository mediaVariantRepository;
    private final MediaRevisionRepository mediaRevisionRepository;
    private final MediaObjectStorage mediaObjectStorage;
    private final MediaProperties properties;
    private final PostImageRepository postImageRepository;
    private final UserRepository userRepository;

    @Transactional
    public MediaUploadResponse initiateUpload(Long userId, MediaUploadRequest request) {
        validateUpload(request);
        String contentType = canonicalContentType(request.contentType());
        MediaAsset asset = new MediaAsset(
                userId,
                request.purpose(),
                request.frame(),
                request.rotation(),
                request.crop().x(),
                request.crop().y(),
                request.crop().width(),
                request.crop().height(),
                contentType,
                request.fileSize(),
                properties.transformVersion(),
                request.operationId()
        );
        mediaAssetRepository.save(asset);
        mediaRevisionRepository.save(MediaRevision.initial(
                asset,
                request.zoom(),
                request.position().x(),
                request.position().y()
        ));
        long maximumSize = maximumSize(request.purpose());
        PresignedPostResponse upload = mediaObjectStorage.createUpload(
                asset.getSourceKey(),
                contentType,
                maximumSize,
                Duration.ofSeconds(properties.uploadExpirationSeconds())
        );
        return new MediaUploadResponse(asset.getMediaId(), asset.getStatus(), upload);
    }

    @Transactional(noRollbackFor = CustomException.class)
    public MediaResponse completeUpload(Long userId, UUID mediaId) {
        MediaAsset asset = findOwnedForUpdate(userId, mediaId);
        if (asset.getStatus() != MediaStatus.PENDING_UPLOAD
                && asset.getStatus() != MediaStatus.UPLOADED
                && asset.getStatus() != MediaStatus.PROCESSING) {
            return toResponse(asset);
        }
        HeadMediaObject object = mediaObjectStorage.head(asset.getSourceKey());
        if (object.contentLength() < 1 || object.contentLength() > maximumSize(asset.getPurpose())) {
            asset.markFailed(ErrorCode.IMAGE_TOO_LARGE.getCode());
            throw new CustomException(ErrorCode.IMAGE_TOO_LARGE);
        }
        if (object.contentLength() != asset.getDeclaredFileSize()) {
            asset.markFailed(ErrorCode.CORRUPTED_IMAGE.getCode());
            throw new CustomException(ErrorCode.CORRUPTED_IMAGE);
        }
        if (!asset.getDeclaredContentType().equals(object.contentType())) {
            asset.markFailed(ErrorCode.UNSUPPORTED_IMAGE_TYPE.getCode());
            throw new CustomException(ErrorCode.UNSUPPORTED_IMAGE_TYPE);
        }
        asset.markUploaded();
        mediaRevisionRepository.findByMediaAssetMediaIdAndRevision(mediaId, 1)
                .ifPresent(MediaRevision::markUploaded);
        return toResponse(asset);
    }

    public MediaResponse getOwned(Long userId, UUID mediaId) {
        return toResponse(findOwned(userId, mediaId));
    }

    @Transactional
    public void cancel(Long userId, UUID mediaId) {
        MediaAsset asset = findOwnedForUpdate(userId, mediaId);
        if (isReferenced(mediaId)) {
            throw new CustomException(ErrorCode.MEDIA_IN_USE);
        }
        deleteAssetObjects(asset);
    }

    @Transactional
    public void deleteIfUnreferenced(Long userId, UUID mediaId) {
        MediaAsset asset = findOwnedForUpdate(userId, mediaId);
        if (!isReferenced(mediaId)) {
            deleteAssetObjects(asset);
        }
    }

    private boolean isReferenced(UUID mediaId) {
        return postImageRepository.existsByMediaAssetMediaId(mediaId)
                || postImageRepository.existsByPendingMediaMediaId(mediaId)
                || userRepository.existsByProfileMediaMediaId(mediaId);
    }

    private void deleteAssetObjects(MediaAsset asset) {
        asset.requestDelete();
        mediaObjectStorage.delete(asset.getSourceKey());
        if (asset.getMasterKey() != null) {
            mediaObjectStorage.delete(asset.getMasterKey());
        }
        List<MediaVariant> variants = mediaVariantRepository
                .findAllByMediaAssetMediaIdOrderByWidthAsc(asset.getMediaId());
        variants.forEach(variant -> mediaObjectStorage.delete(variant.getObjectKey()));
        if (!variants.isEmpty()) {
            mediaObjectStorage.invalidate(List.of("/media/" + asset.getMediaId() + "/*"));
        }
        mediaVariantRepository.deleteAllByMediaAssetMediaId(asset.getMediaId());
        mediaRevisionRepository.findAllByMediaAssetMediaId(asset.getMediaId())
                .forEach(revision -> {
                    if (revision.getRevision() != asset.getMediaRevision()) {
                        try {
                            revision.requestDelete();
                        } catch (IllegalStateException ignored) {
                            // All revisions are deleted with their owning Media asset.
                        }
                    }
                    revision.markDeleted();
                });
        asset.markDeleted();
    }

    public List<MediaAsset> requireReadyMedia(
            Long ownerUserId,
            MediaPurpose purpose,
            List<UUID> mediaIds
    ) {
        List<UUID> ids = mediaIds == null ? List.of() : List.copyOf(mediaIds);
        if (ids.size() > 5 || new HashSet<>(ids).size() != ids.size()) {
            throw new CustomException(ErrorCode.INVALID_POST_REQUEST);
        }
        List<MediaAsset> assets = ids.stream()
                .map(id -> findOwnedForUpdate(ownerUserId, id))
                .toList();
        for (MediaAsset asset : assets) {
            if (asset.getPurpose() != purpose) {
                throw new CustomException(ErrorCode.MEDIA_PURPOSE_MISMATCH);
            }
            if (asset.getStatus() != MediaStatus.READY) {
                throw new CustomException(ErrorCode.MEDIA_NOT_READY);
            }
        }
        return assets;
    }

    public List<MediaAsset> requireAttachableMedia(
            Long ownerUserId,
            MediaPurpose purpose,
            List<UUID> mediaIds
    ) {
        List<UUID> ids = mediaIds == null ? List.of() : List.copyOf(mediaIds);
        if (ids.size() > 5 || new HashSet<>(ids).size() != ids.size()) {
            throw new CustomException(ErrorCode.INVALID_POST_REQUEST);
        }
        List<MediaAsset> assets = ids.stream()
                .map(id -> findOwnedForUpdate(ownerUserId, id))
                .toList();
        for (MediaAsset asset : assets) {
            if (asset.getPurpose() != purpose) {
                throw new CustomException(ErrorCode.MEDIA_PURPOSE_MISMATCH);
            }
            if (!Set.of(MediaStatus.UPLOADED, MediaStatus.PROCESSING, MediaStatus.READY)
                    .contains(asset.getStatus())) {
                throw new CustomException(ErrorCode.MEDIA_NOT_READY);
            }
        }
        return assets;
    }

    public MediaResponse toResponse(MediaAsset asset) {
        List<MediaVariantResponse> variants = mediaVariantRepository
                .findAllByMediaAssetMediaIdAndMediaRevisionOrderByWidthAsc(
                        asset.getMediaId(), asset.getMediaRevision()
                )
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
        return new MediaResponse(
                asset.getMediaId(),
                asset.getStatus(),
                asset.getFrame(),
                asset.getMediaRevision(),
                asset.getTransformVersion(),
                asset.getErrorCode(),
                variants
        );
    }

    public String compatibilityUrl(MediaAsset asset) {
        List<MediaVariant> variants = mediaVariantRepository
                .findAllByMediaAssetMediaIdAndMediaRevisionOrderByWidthAsc(
                        asset.getMediaId(), asset.getMediaRevision()
                );
        MediaVariantType preferred = asset.getPurpose() == MediaPurpose.PROFILE
                ? MediaVariantType.PROFILE_MEDIUM
                : asset.getFrame() == MediaFrame.POST_PORTRAIT
                ? MediaVariantType.POST_PORTRAIT_1X
                : MediaVariantType.POST_LANDSCAPE_1X;
        return variants.stream()
                .filter(variant -> variant.getVariantType() == preferred)
                .findFirst()
                .or(() -> variants.stream().findFirst())
                .map(variant -> publicUrl(variant.getObjectKey()))
                .orElseThrow(() -> new CustomException(ErrorCode.MEDIA_NOT_READY));
    }

    private MediaAsset findOwned(Long userId, UUID mediaId) {
        MediaAsset asset = mediaAssetRepository.findById(mediaId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEDIA_NOT_FOUND));
        if (!asset.ownedBy(userId)) {
            throw new CustomException(ErrorCode.UPLOAD_NOT_OWNED);
        }
        return asset;
    }

    private MediaAsset findOwnedForUpdate(Long userId, UUID mediaId) {
        MediaAsset asset = mediaAssetRepository.findByIdForUpdate(mediaId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEDIA_NOT_FOUND));
        if (!asset.ownedBy(userId)) {
            throw new CustomException(ErrorCode.UPLOAD_NOT_OWNED);
        }
        return asset;
    }

    private void validateUpload(MediaUploadRequest request) {
        if (!properties.enabled()) {
            throw new CustomException(ErrorCode.MEDIA_V2_DISABLED);
        }
        String contentType = canonicalContentType(request.contentType());
        if (!CONTENT_TYPES.contains(contentType)) {
            throw new CustomException(ErrorCode.UNSUPPORTED_IMAGE_TYPE);
        }
        String extension = extension(request.fileName());
        if (!EXTENSIONS.get(contentType).contains(extension)) {
            throw new CustomException(ErrorCode.UNSUPPORTED_IMAGE_TYPE);
        }
        if (request.fileSize() > maximumSize(request.purpose())) {
            throw new CustomException(ErrorCode.IMAGE_TOO_LARGE);
        }
        if ((request.purpose() == MediaPurpose.PROFILE && request.frame() != MediaFrame.PROFILE)
                || (request.purpose() == MediaPurpose.POST && request.frame() == MediaFrame.PROFILE)) {
            throw new CustomException(ErrorCode.MEDIA_PURPOSE_MISMATCH);
        }
        if (!Set.of(0, 90, 180, 270).contains(request.rotation())) {
            throw new CustomException(ErrorCode.INVALID_CROP_RECT);
        }
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

    private long maximumSize(MediaPurpose purpose) {
        return purpose == MediaPurpose.PROFILE ? PROFILE_MAX_SIZE : POST_MAX_SIZE;
    }

    private String extension(String fileName) {
        int separator = fileName.lastIndexOf('.');
        return separator < 0 ? "" : fileName.substring(separator + 1).toLowerCase(Locale.ROOT);
    }

    private String canonicalContentType(String contentType) {
        return "image/x-ms-bmp".equals(contentType) ? "image/bmp" : contentType;
    }

    private String publicUrl(String objectKey) {
        String base = properties.cdnBaseUrl() == null ? "" : properties.cdnBaseUrl().replaceAll("/+$", "");
        String path = objectKey.startsWith("public/") ? objectKey.substring("public/".length()) : objectKey;
        return base + "/" + path;
    }
}
