package com.ian.community.post.controller;

import com.ian.community.common.ApiResponse;
import com.ian.community.common.media.MediaService;
import com.ian.community.post.domain.Post;
import com.ian.community.post.dto.request.PostCreateV2Request;
import com.ian.community.post.dto.request.PostUpdateV2Request;
import com.ian.community.post.dto.response.*;
import com.ian.community.post.service.*;
import com.ian.community.security.principal.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v2/posts")
@RequiredArgsConstructor
public class PostControllerV2 {
    private final PostService postService;
    private final CommentService commentService;
    private final PostLikeService postLikeService;
    private final BookmarkService bookmarkService;
    private final MediaService mediaService;

    @PostMapping("/me")
    public ResponseEntity<Long> create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody PostCreateV2Request request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(postService.createPostV2(user.getUserId(), request.content(), request.mediaIds()));
    }

    @PatchMapping("/{postId}")
    public ResponseEntity<Void> update(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long postId,
            @Valid @RequestBody PostUpdateV2Request request
    ) {
        postService.updatePostV2(
                user.getUserId(),
                postId,
                request.content(),
                request.mediaIds(),
                request.revisionActivations()
        );
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<ApiResponse<SliceResponse<PostV2Response>>> list(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PageableDefault(size = 10) Pageable pageable
    ) {
        Slice<Post> posts = postService.getPosts(limit(pageable));
        List<Long> ids = posts.getContent().stream().map(Post::getPostId).toList();
        Set<Long> bookmarked = bookmarkService.findBookmarkedPostIds(user.getUserId(), ids);
        Set<Long> liked = postLikeService.findLikedPostIds(user.getUserId(), ids);
        Slice<PostV2Response> response = posts.map(post -> map(
                post,
                liked.contains(post.getPostId()),
                bookmarked.contains(post.getPostId()),
                List.of()
        ));
        PostSuccessCode code = response.hasNext()
                ? PostSuccessCode.POST_LIST_FOUND
                : PostSuccessCode.NO_MORE_POSTS;
        return ResponseEntity.ok(ApiResponse.success(
                code,
                SliceResponse.from(response, PostSuccessCode.NO_MORE_POSTS.getMessage())
        ));
    }

    @GetMapping("/{postId}")
    public ResponseEntity<PostV2Response> detail(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long postId
    ) {
        Post post = postService.getPostDetail(user.getUserId(), postId);
        List<PostCommentResponse> comments = commentService.getComments(postId, Pageable.unpaged())
                .map(PostCommentResponse::from)
                .getContent();
        return ResponseEntity.ok(map(
                post,
                postLikeService.isLiked(user.getUserId(), postId),
                bookmarkService.existsBookmark(user.getUserId(), postId),
                comments
        ));
    }

    @GetMapping("/bookmarks")
    public ResponseEntity<ApiResponse<SliceResponse<PostV2Response>>> bookmarks(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PageableDefault(size = 10) Pageable pageable
    ) {
        Slice<Post> posts = bookmarkService.getBookmarkPosts(user.getUserId(), limit(pageable));
        Set<Long> liked = postLikeService.findLikedPostIds(
                user.getUserId(),
                posts.getContent().stream().map(Post::getPostId).toList()
        );
        Slice<PostV2Response> response = posts.map(post -> map(
                post,
                liked.contains(post.getPostId()),
                true,
                List.of()
        ));
        BookmarkSuccessCode code = response.hasNext()
                ? BookmarkSuccessCode.BOOKMARK_LIST_FOUND
                : BookmarkSuccessCode.NO_MORE_BOOKMARKS;
        return ResponseEntity.ok(ApiResponse.success(
                code,
                SliceResponse.from(response, BookmarkSuccessCode.NO_MORE_BOOKMARKS.getMessage())
        ));
    }

    private PostV2Response map(
            Post post,
            boolean liked,
            boolean bookmarked,
            List<PostCommentResponse> comments
    ) {
        var profile = post.getAuthorUser().getProfileMedia() == null
                ? null
                : mediaService.toResponse(post.getAuthorUser().getProfileMedia());
        return PostV2Response.from(
                post,
                AuthorV2Response.from(post.getAuthorUser(), profile),
                postService.getPostMedia(post),
                postService.getPostImageUrl(post),
                liked,
                bookmarked,
                comments
        );
    }

    private Pageable limit(Pageable pageable) {
        return PageRequest.of(
                Math.max(pageable.getPageNumber(), 0),
                Math.min(Math.max(pageable.getPageSize(), 1), 10)
        );
    }
}
