package com.ian.community.common.media;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Getter
@Entity
@Table(name = "media_assets", indexes = {
        @Index(name = "idx_media_assets_owner_status", columnList = "owner_user_id,status"),
        @Index(name = "idx_media_assets_status_lease", columnList = "status,lease_until")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MediaAsset {
    @Id
    @Column(name = "media_id", nullable = false, updatable = false)
    private UUID mediaId;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MediaPurpose purpose;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MediaStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MediaFrame frame;

    @Column(nullable = false)
    private int rotation;

    @Column(name = "crop_x", precision = 12, scale = 9, nullable = false)
    private BigDecimal cropX;

    @Column(name = "crop_y", precision = 12, scale = 9, nullable = false)
    private BigDecimal cropY;

    @Column(name = "crop_width", precision = 12, scale = 9, nullable = false)
    private BigDecimal cropWidth;

    @Column(name = "crop_height", precision = 12, scale = 9, nullable = false)
    private BigDecimal cropHeight;

    @Column(name = "source_key", length = 500, nullable = false, unique = true)
    private String sourceKey;

    @Column(name = "master_key", length = 500)
    private String masterKey;

    @Column(name = "declared_content_type", length = 50, nullable = false)
    private String declaredContentType;

    @Column(name = "declared_file_size", nullable = false)
    private long declaredFileSize;

    @Column(name = "source_width")
    private Integer sourceWidth;

    @Column(name = "source_height")
    private Integer sourceHeight;

    @Column(name = "source_format", length = 20)
    private String sourceFormat;

    @Column(name = "output_content_type", length = 50)
    private String outputContentType;

    @Column(name = "operation_id", nullable = false)
    private UUID operationId;

    @Column(name = "active_revision", nullable = false)
    private int mediaRevision;

    @Column(name = "latest_revision", nullable = false)
    private int latestRevision;

    @Column(name = "transform_version", nullable = false)
    private int transformVersion;

    @Column(name = "error_code", length = 80)
    private String errorCode;

    @Column(name = "lease_until")
    private LocalDateTime leaseUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Version
    @Column(name = "record_version", nullable = false)
    private long recordVersion;

    public MediaAsset(
            Long ownerUserId,
            MediaPurpose purpose,
            MediaFrame frame,
            int rotation,
            BigDecimal cropX,
            BigDecimal cropY,
            BigDecimal cropWidth,
            BigDecimal cropHeight,
            String declaredContentType,
            long declaredFileSize,
            int transformVersion
    ) {
        this(
                ownerUserId, purpose, frame, rotation,
                cropX, cropY, cropWidth, cropHeight,
                declaredContentType, declaredFileSize, transformVersion,
                UUID.randomUUID()
        );
    }

    public MediaAsset(
            Long ownerUserId,
            MediaPurpose purpose,
            MediaFrame frame,
            int rotation,
            BigDecimal cropX,
            BigDecimal cropY,
            BigDecimal cropWidth,
            BigDecimal cropHeight,
            String declaredContentType,
            long declaredFileSize,
            int transformVersion,
            UUID operationId
    ) {
        this.mediaId = UUID.randomUUID();
        this.ownerUserId = ownerUserId;
        this.purpose = purpose;
        this.status = MediaStatus.PENDING_UPLOAD;
        this.frame = frame;
        this.rotation = rotation;
        this.cropX = cropX;
        this.cropY = cropY;
        this.cropWidth = cropWidth;
        this.cropHeight = cropHeight;
        this.sourceKey = "private/uploads/" + mediaId + "/source";
        this.declaredContentType = declaredContentType;
        this.declaredFileSize = declaredFileSize;
        this.mediaRevision = 1;
        this.latestRevision = 1;
        this.transformVersion = transformVersion;
        this.operationId = operationId;
        this.createdAt = now();
        this.updatedAt = createdAt;
    }

    public boolean ownedBy(Long userId) {
        return ownerUserId.equals(userId);
    }

    public void markUploaded() {
        if (status == MediaStatus.PENDING_UPLOAD) {
            status = MediaStatus.UPLOADED;
            touch();
        }
    }

    public boolean claim(LocalDateTime lease) {
        if (status == MediaStatus.PENDING_UPLOAD || status == MediaStatus.READY
                || status == MediaStatus.FAILED || status == MediaStatus.PENDING_DELETE
                || status == MediaStatus.DELETED) {
            return false;
        }
        if (status == MediaStatus.PROCESSING && leaseUntil != null && leaseUntil.isAfter(now())) {
            return false;
        }
        status = MediaStatus.PROCESSING;
        leaseUntil = lease;
        errorCode = null;
        touch();
        return true;
    }

    public void markReady(
            String masterKey,
            int width,
            int height,
            String sourceFormat,
            String outputContentType
    ) {
        this.masterKey = masterKey;
        this.sourceWidth = width;
        this.sourceHeight = height;
        this.sourceFormat = sourceFormat;
        this.outputContentType = outputContentType;
        this.status = MediaStatus.READY;
        this.leaseUntil = null;
        this.errorCode = null;
        touch();
    }

    public void markReady(String masterKey, int width, int height) {
        markReady(masterKey, width, height, null, null);
    }

    public int allocateRevision() {
        latestRevision++;
        touch();
        return latestRevision;
    }

    public void activate(MediaRevision revision) {
        if (!mediaId.equals(revision.getMediaAsset().getMediaId())
                || revision.getStatus() != MediaStatus.READY) {
            throw new IllegalArgumentException("Only a ready revision of this media can be activated");
        }
        this.frame = revision.getFrame();
        this.rotation = revision.getRotation();
        this.cropX = revision.getCropX();
        this.cropY = revision.getCropY();
        this.cropWidth = revision.getCropWidth();
        this.cropHeight = revision.getCropHeight();
        this.mediaRevision = revision.getRevision();
        this.transformVersion = revision.getTransformVersion();
        this.status = MediaStatus.READY;
        this.errorCode = null;
        touch();
        revision.markActivated();
    }

    public void markFailed(String errorCode) {
        if (status == MediaStatus.READY || status == MediaStatus.PENDING_DELETE
                || status == MediaStatus.DELETED) {
            return;
        }
        this.status = MediaStatus.FAILED;
        this.errorCode = errorCode;
        this.leaseUntil = null;
        touch();
    }

    public void requestDelete() {
        if (status != MediaStatus.DELETED) {
            status = MediaStatus.PENDING_DELETE;
            leaseUntil = null;
            touch();
        }
    }

    public void markDeleted() {
        status = MediaStatus.DELETED;
        deletedAt = now();
        touch();
    }

    private void touch() {
        updatedAt = now();
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
