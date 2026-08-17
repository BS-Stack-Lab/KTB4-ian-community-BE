package com.ian.community.post.controller;

import com.ian.community.common.ApiResponse;
import com.ian.community.common.media.MediaService;
import com.ian.community.post.domain.Post;
import com.ian.community.post.dto.response.AuthorV2Response;
import com.ian.community.post.dto.response.PostCommentResponse;
import com.ian.community.post.dto.response.PostSuccessCode;
import com.ian.community.post.dto.response.PostV2Response;
import com.ian.community.post.dto.response.SliceResponse;
import com.ian.community.post.service.BookmarkService;
import com.ian.community.post.service.PostLikeService;
import com.ian.community.post.service.PostService;
import com.ian.community.security.principal.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v2/users")
@RequiredArgsConstructor
public class UserPostControllerV2 {
    private final PostService postService;
    private final PostLikeService postLikeService;
    private final BookmarkService bookmarkService;
    private final MediaService mediaService;

    @GetMapping("/{userId}/posts")
    public ResponseEntity<ApiResponse<SliceResponse<PostV2Response>>> posts(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable Long userId,
            @PageableDefault(size = 10) Pageable pageable
    ) {
        Slice<Post> posts = postService.getPostsByUser(userId, limit(pageable));
        List<Long> ids = posts.getContent().stream().map(Post::getPostId).toList();
        Set<Long> bookmarked = bookmarkService.findBookmarkedPostIds(
                authenticatedUser.getUserId(),
                ids
        );
        Set<Long> liked = postLikeService.findLikedPostIds(
                authenticatedUser.getUserId(),
                ids
        );
        Slice<PostV2Response> response = posts.map(post -> map(
                post,
                liked.contains(post.getPostId()),
                bookmarked.contains(post.getPostId())
        ));
        PostSuccessCode code = response.hasNext()
                ? PostSuccessCode.POST_LIST_FOUND
                : PostSuccessCode.NO_MORE_POSTS;
        return ResponseEntity.ok(ApiResponse.success(
                code,
                SliceResponse.from(response, PostSuccessCode.NO_MORE_POSTS.getMessage())
        ));
    }

    private PostV2Response map(Post post, boolean liked, boolean bookmarked) {
        var profile = post.getAuthorUser().getProfileMedia() == null
                ? null
                : mediaService.toResponse(post.getAuthorUser().getProfileMedia());
        return PostV2Response.from(
                post,
                AuthorV2Response.from(post.getAuthorUser(), profile),
                postService.getPostMedia(post),
                postService.getPostMediaAttachments(post),
                postService.getPostImageUrl(post),
                liked,
                bookmarked,
                List.<PostCommentResponse>of()
        );
    }

    private Pageable limit(Pageable pageable) {
        return PageRequest.of(
                Math.max(pageable.getPageNumber(), 0),
                Math.min(Math.max(pageable.getPageSize(), 1), 10)
        );
    }
}
