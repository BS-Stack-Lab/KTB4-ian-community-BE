package com.ian.community.common.media;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Getter
@Entity
@Table(name = "media_variants", uniqueConstraints = @UniqueConstraint(
        name = "uk_media_variants_asset_type_revision_transform",
        columnNames = {"media_id", "variant_type", "media_revision", "transform_version"}
))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MediaVariant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "media_variant_id")
    private Long mediaVariantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "media_id", nullable = false)
    private MediaAsset mediaAsset;

    @Enumerated(EnumType.STRING)
    @Column(name = "variant_type", nullable = false, length = 40)
    private MediaVariantType variantType;

    @Column(name = "object_key", nullable = false, length = 500)
    private String objectKey;

    @Column(nullable = false)
    private int width;

    @Column(nullable = false)
    private int height;

    @Column(name = "mime_type", nullable = false, length = 50)
    private String mimeType;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "media_revision", nullable = false)
    private int mediaRevision;

    @Column(name = "transform_version", nullable = false)
    private int transformVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public MediaVariant(
            MediaAsset mediaAsset,
            MediaRevision revision,
            MediaVariantType variantType,
            String objectKey,
            long fileSize
    ) {
        this.mediaAsset = mediaAsset;
        this.variantType = variantType;
        this.objectKey = objectKey;
        this.width = variantType.getWidth();
        this.height = variantType.getHeight();
        this.mimeType = "image/webp";
        this.fileSize = fileSize;
        this.mediaRevision = revision.getRevision();
        this.transformVersion = revision.getTransformVersion();
        this.createdAt = LocalDateTime.now(ZoneOffset.UTC);
    }
}
