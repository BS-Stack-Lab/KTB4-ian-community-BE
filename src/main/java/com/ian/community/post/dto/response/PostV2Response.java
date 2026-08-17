package com.ian.community.post.dto.response;

import com.ian.community.common.media.dto.MediaResponse;
import com.ian.community.post.domain.Post;

import java.time.LocalDateTime;
import java.util.List;

public record PostV2Response(
        Long postId,
        String content,
        AuthorV2Response author,
        List<MediaResponse> media,
        List<PostMediaAttachmentResponse> mediaAttachments,
        String legacyImageUrl,
        int likeCount,
        int commentCount,
        int viewCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        boolean liked,
        boolean bookmarked,
        List<PostCommentResponse> comments
) {
    public static PostV2Response from(
            Post post,
            AuthorV2Response author,
            List<MediaResponse> media,
            List<PostMediaAttachmentResponse> mediaAttachments,
            String legacyImageUrl,
            boolean liked,
            boolean bookmarked,
            List<PostCommentResponse> comments
    ) {
        return new PostV2Response(
                post.getPostId(),
                post.getContent(),
                author,
                List.copyOf(media),
                List.copyOf(mediaAttachments),
                media.isEmpty() ? legacyImageUrl : null,
                post.getLikeCount(),
                post.getCommentCount(),
                post.getViewCount(),
                post.getCreatedAt(),
                post.getUpdatedAt(),
                liked,
                bookmarked,
                comments == null ? List.of() : List.copyOf(comments)
        );
    }
}
