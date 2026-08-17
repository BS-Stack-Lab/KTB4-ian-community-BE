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
@Table(name = "media_revisions", uniqueConstraints = @UniqueConstraint(
        name = "uk_media_revisions_asset_revision",
        columnNames = {"media_id", "revision"}
), indexes = @Index(
        name = "idx_media_revisions_status_lease",
        columnList = "status,lease_until"
))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MediaRevision {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "media_revision_id")
    private Long mediaRevisionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "media_id", nullable = false)
    private MediaAsset mediaAsset;

    @Column(nullable = false)
    private int revision;

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

    @Column(precision = 6, scale = 3, nullable = false)
    private BigDecimal zoom;

    @Column(name = "position_x", precision = 12, scale = 9, nullable = false)
    private BigDecimal positionX;

    @Column(name = "position_y", precision = 12, scale = 9, nullable = false)
    private BigDecimal positionY;

    @Column(name = "transform_version", nullable = false)
    private int transformVersion;

    @Column(name = "crop_pixel_width")
    private Integer cropPixelWidth;

    @Column(name = "crop_pixel_height")
    private Integer cropPixelHeight;

    @Enumerated(EnumType.STRING)
    @Column(name = "quality_level", length = 30)
    private MediaQualityLevel qualityLevel;

    @Column(name = "upscale_ratio_1x", precision = 8, scale = 4)
    private BigDecimal upscaleRatio1x;

    @Column(name = "operation_id", nullable = false)
    private UUID operationId;

    @Column(name = "error_code", length = 80)
    private String errorCode;

    @Column(name = "lease_until")
    private LocalDateTime leaseUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "activated_at")
    private LocalDateTime activatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Version
    @Column(name = "record_version", nullable = false)
    private long recordVersion;

    public MediaRevision(
            MediaAsset mediaAsset,
            int revision,
            MediaStatus status,
            MediaFrame frame,
            int rotation,
            BigDecimal cropX,
            BigDecimal cropY,
            BigDecimal cropWidth,
            BigDecimal cropHeight,
            BigDecimal zoom,
            BigDecimal positionX,
            BigDecimal positionY,
            int transformVersion
    ) {
        this(
                mediaAsset, revision, status, frame, rotation,
                cropX, cropY, cropWidth, cropHeight,
                zoom, positionX, positionY, transformVersion,
                UUID.randomUUID()
        );
    }

    public MediaRevision(
            MediaAsset mediaAsset,
            int revision,
            MediaStatus status,
            MediaFrame frame,
            int rotation,
            BigDecimal cropX,
            BigDecimal cropY,
            BigDecimal cropWidth,
            BigDecimal cropHeight,
            BigDecimal zoom,
            BigDecimal positionX,
            BigDecimal positionY,
            int transformVersion,
            UUID operationId
    ) {
        this.mediaAsset = mediaAsset;
        this.revision = revision;
        this.status = status;
        this.frame = frame;
        this.rotation = rotation;
        this.cropX = cropX;
        this.cropY = cropY;
        this.cropWidth = cropWidth;
        this.cropHeight = cropHeight;
        this.zoom = zoom;
        this.positionX = positionX;
        this.positionY = positionY;
        this.transformVersion = transformVersion;
        this.operationId = operationId;
        this.createdAt = now();
        this.updatedAt = createdAt;
    }

    public static MediaRevision initial(MediaAsset asset) {
        BigDecimal centerX = asset.getCropX().add(asset.getCropWidth().divide(BigDecimal.valueOf(2)));
        BigDecimal centerY = asset.getCropY().add(asset.getCropHeight().divide(BigDecimal.valueOf(2)));
        return initial(asset, BigDecimal.ONE, centerX, centerY);
    }

    public static MediaRevision initial(
            MediaAsset asset,
            BigDecimal zoom,
            BigDecimal positionX,
            BigDecimal positionY
    ) {
        return new MediaRevision(
                asset, 1, MediaStatus.PENDING_UPLOAD, asset.getFrame(), asset.getRotation(),
                asset.getCropX(), asset.getCropY(), asset.getCropWidth(), asset.getCropHeight(),
                zoom, positionX, positionY, asset.getTransformVersion(), asset.getOperationId()
        );
    }

    public void markUploaded() {
        if (status == MediaStatus.PENDING_UPLOAD) {
            status = MediaStatus.UPLOADED;
            touch();
        }
    }

    public boolean claim(LocalDateTime lease) {
        if (status == MediaStatus.READY || status == MediaStatus.FAILED
                || status == MediaStatus.PENDING_DELETE || status == MediaStatus.DELETED) {
            return false;
        }
        if (status == MediaStatus.PENDING_UPLOAD) {
            throw new IllegalStateException("Revision source is not uploaded");
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

    public void markReady() {
        if (status == MediaStatus.PROCESSING) {
            status = MediaStatus.READY;
            leaseUntil = null;
            errorCode = null;
            touch();
        }
    }

    public void markReady(
            int cropPixelWidth,
            int cropPixelHeight,
            MediaQualityLevel qualityLevel,
            BigDecimal upscaleRatio1x
    ) {
        this.cropPixelWidth = cropPixelWidth;
        this.cropPixelHeight = cropPixelHeight;
        this.qualityLevel = qualityLevel;
        this.upscaleRatio1x = upscaleRatio1x;
        markReady();
    }

    public void markFailed(String code) {
        if (status == MediaStatus.READY || status == MediaStatus.PENDING_DELETE
                || status == MediaStatus.DELETED) {
            return;
        }
        status = MediaStatus.FAILED;
        errorCode = code;
        leaseUntil = null;
        touch();
    }

    public void releaseForRetry() {
        if (status == MediaStatus.PROCESSING) {
            status = MediaStatus.UPLOADED;
            leaseUntil = null;
            touch();
        }
    }

    public void requestDelete() {
        if (activatedAt != null) {
            throw new IllegalStateException("Active revision cannot be deleted");
        }
        status = MediaStatus.PENDING_DELETE;
        leaseUntil = null;
        touch();
    }

    public void markDeleted() {
        status = MediaStatus.DELETED;
        deletedAt = now();
        touch();
    }

    public void markActivated() {
        activatedAt = now();
        touch();
    }

    private void touch() {
        updatedAt = now();
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
