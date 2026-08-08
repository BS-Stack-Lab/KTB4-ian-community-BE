package com.ian.community.post.domain;

import com.ian.community.common.media.MediaAsset;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.ZoneId;

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

    @Column(name = "image_url", length = 500, nullable = false)
    private String imageUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_id")
    private MediaAsset mediaAsset;

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
        this.displayOrder = displayOrder;
        this.createdAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
    }

    public void updateImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
        this.mediaAsset = null;
        this.displayOrder = 0;
    }
}
