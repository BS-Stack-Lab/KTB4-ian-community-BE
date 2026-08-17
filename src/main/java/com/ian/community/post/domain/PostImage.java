package com.ian.community.post.domain;

import com.ian.community.common.media.MediaAsset;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "post_images", uniqueConstraints = @UniqueConstraint(
        name = "uk_post_images_post_order",
        columnNames = {"post_id", "display_order"}
))
public class PostImage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "post_image_id")
    private Long PostImageId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private Post authorPost;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_id")
    private MediaAsset mediaAsset;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pending_media_id")
    private MediaAsset pendingMedia;

    @Column(name = "pending_revision")
    private Integer pendingRevision;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_state", length = 20, nullable = false)
    private PostImageMediaState mediaState;

    @Column(name = "media_error_code", length = 80)
    private String mediaErrorCode;

    @Column(name = "media_operation_id")
    private UUID mediaOperationId;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "create_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public PostImage(Post authorPost, String imageUrl) {
        this(authorPost, imageUrl, null, 0);
    }

    public PostImage(
            Post authorPost,
            String imageUrl,
            MediaAsset mediaAsset,
            int displayOrder
    ) {
        this.authorPost = authorPost;
        this.imageUrl = imageUrl;
        this.mediaAsset = mediaAsset;
        this.mediaState = PostImageMediaState.READY;
        this.displayOrder = displayOrder;
        this.createdAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
    }

    public void updateImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
        this.mediaAsset = null;
        clearPending();
        this.mediaState = PostImageMediaState.READY;
        this.displayOrder = 0;
    }

    public static PostImage pending(
            Post post,
            MediaAsset pendingMedia,
            int displayOrder,
            UUID operationId
    ) {
        PostImage image = new PostImage(post, null, null, displayOrder);
        image.pendingMedia = pendingMedia;
        image.pendingRevision = 1;
        image.mediaState = PostImageMediaState.PROCESSING;
        image.mediaOperationId = operationId;
        return image;
    }

    public void replaceWithReady(String imageUrl, MediaAsset mediaAsset, int displayOrder) {
        this.imageUrl = imageUrl;
        this.mediaAsset = mediaAsset;
        this.displayOrder = displayOrder;
        clearPending();
        this.mediaState = PostImageMediaState.READY;
    }

    public void replaceWithPending(MediaAsset pendingMedia, int displayOrder, UUID operationId) {
        this.pendingMedia = pendingMedia;
        this.pendingRevision = 1;
        this.displayOrder = displayOrder;
        this.mediaState = PostImageMediaState.PROCESSING;
        this.mediaErrorCode = null;
        this.mediaOperationId = operationId;
    }

    public void requestRevision(MediaAsset mediaAsset, int revision, UUID operationId) {
        this.pendingMedia = mediaAsset;
        this.pendingRevision = revision;
        this.mediaState = PostImageMediaState.PROCESSING;
        this.mediaErrorCode = null;
        this.mediaOperationId = operationId;
    }

    public void promotePending(String imageUrl) {
        if (pendingMedia == null) {
            return;
        }
        this.imageUrl = imageUrl;
        this.mediaAsset = pendingMedia;
        clearPending();
        this.mediaState = PostImageMediaState.READY;
    }

    public void failPending(String errorCode) {
        clearPendingTarget();
        this.mediaState = PostImageMediaState.FAILED;
        this.mediaErrorCode = errorCode;
    }

    private void clearPending() {
        clearPendingTarget();
        this.mediaState = PostImageMediaState.READY;
        this.mediaErrorCode = null;
    }

    private void clearPendingTarget() {
        this.pendingMedia = null;
        this.pendingRevision = null;
        this.mediaOperationId = null;
    }
}
