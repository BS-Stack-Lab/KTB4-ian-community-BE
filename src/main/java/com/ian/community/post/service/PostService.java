package com.ian.community.post.service;

import com.ian.community.common.exception.CustomException;
import com.ian.community.common.exception.ErrorCode;
import com.ian.community.common.media.MediaAsset;
import com.ian.community.common.media.MediaPurpose;
import com.ian.community.common.media.MediaService;
import com.ian.community.common.media.MediaRevisionService;
import com.ian.community.common.media.dto.MediaRevisionActivationRequest;
import com.ian.community.common.media.dto.MediaRevisionTargetRequest;
import com.ian.community.common.media.dto.MediaResponse;
import com.ian.community.post.domain.Post;
import com.ian.community.post.domain.PostImage;
import com.ian.community.post.dto.response.PostMediaAttachmentResponse;
import com.ian.community.post.domain.PostView;
import com.ian.community.post.repository.PostImageRepository;
import com.ian.community.post.repository.PostRepository;
import com.ian.community.post.repository.PostViewRepository;
import com.ian.community.user.domain.User;
import com.ian.community.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final PostImageRepository postImageRepository;
    private final PostViewRepository postViewRepository;
    private final MediaService mediaService;
    private final MediaRevisionService mediaRevisionService;

    @Transactional
    public Long createPost(Long userId, String content, String imageUrl) {
        User user = getActiveUser(userId);

        Post post = new Post(user, content);
        Post savedPost = postRepository.save(post);

        if (imageUrl != null && !imageUrl.isBlank()) {
            PostImage postImage = new PostImage(savedPost, imageUrl);
            postImageRepository.save(postImage);
        }

        return savedPost.getPostId();
    }

    @Transactional
    public Long createPostV2(Long userId, String content, List<UUID> mediaIds) {
        User user = getActiveUser(userId);
        List<MediaAsset> media = mediaService.requireReadyMedia(
                userId, MediaPurpose.POST, mediaIds
        );
        Post savedPost = postRepository.save(new Post(user, content));
        for (int index = 0; index < media.size(); index++) {
            MediaAsset asset = media.get(index);
            postImageRepository.save(new PostImage(
                    savedPost,
                    mediaService.compatibilityUrl(asset),
                    asset,
                    index
            ));
        }
        return savedPost.getPostId();
    }

    @Transactional
    public Post createPostAsyncMedia(Long userId, String content, List<UUID> mediaIds) {
        User user = getActiveUser(userId);
        List<MediaAsset> media = mediaService.requireAttachableMedia(
                userId, MediaPurpose.POST, mediaIds
        );
        Post savedPost = postRepository.save(new Post(user, content));
        for (int index = 0; index < media.size(); index++) {
            MediaAsset asset = media.get(index);
            PostImage image = asset.getStatus() == com.ian.community.common.media.MediaStatus.READY
                    ? new PostImage(savedPost, mediaService.compatibilityUrl(asset), asset, index)
                    : PostImage.pending(savedPost, asset, index, asset.getOperationId());
            postImageRepository.save(image);
        }
        return savedPost;
    }

    public Slice<Post> getPosts(Pageable pageable) {
        return postRepository
                .findAllByPostDeletedFalseOrderByCreatedAtDescPostIdDesc(
                        pageable
                );
    }

    public String getPostImageUrl(Post post) {
        return postImageRepository.findAllByAuthorPostOrderByDisplayOrderAsc(post)
                .stream()
                .findFirst()
                .map(image -> image.getMediaAsset() == null
                        ? image.getImageUrl()
                        : mediaService.compatibilityUrl(image.getMediaAsset()))
                .orElse(null);
    }

    public List<MediaResponse> getPostMedia(Post post) {
        return postImageRepository.findAllByAuthorPostOrderByDisplayOrderAsc(post)
                .stream()
                .map(PostImage::getMediaAsset)
                .filter(java.util.Objects::nonNull)
                .map(mediaService::toResponse)
                .toList();
    }

    public List<PostMediaAttachmentResponse> getPostMediaAttachments(Post post) {
        return postImageRepository.findAllByAuthorPostOrderByDisplayOrderAsc(post)
                .stream()
                .map(image -> new PostMediaAttachmentResponse(
                        image.getDisplayOrder(),
                        image.getMediaState(),
                        image.getMediaAsset() == null
                                ? null
                                : mediaService.toResponse(image.getMediaAsset()),
                        image.getPendingMedia() == null
                                ? null
                                : image.getPendingMedia().getMediaId(),
                        image.getPendingMedia() == null
                                ? null
                                : image.getPendingMedia().getFrame(),
                        image.getMediaErrorCode()
                ))
                .toList();
    }

    public Slice<Post> getPostsByUser(Long userId, Pageable pageable) {
        getActiveUser(userId);

        return postRepository
                .findAllByAuthorUser_UserIdAndPostDeletedFalseOrderByCreatedAtDescPostIdDesc(
                        userId,
                        pageable
                );
    }

    @Transactional
    public Post getPostDetail(Long userId, Long postId) {
        User user = getActiveUser(userId);
        Post post = getActivePost(postId);

        increaseViewCountIfAllowed(user, post);

        return post;
    }

    @Transactional
    public void updatePost(Long userId, Long postId, String title, String content, String imageUrl) {
        User user = getActiveUser(userId);
        Post post = getActivePost(postId);

        validatePostOwner(post, user);

        boolean sameContent = post.getContent().equals(content);

        if (sameContent) {
            throw new CustomException(ErrorCode.NO_CHANGES_DETECTED);
        }

        post.update(content);

        if (imageUrl != null && !imageUrl.isBlank()) {
            updatePostImage(post, imageUrl);
        }
    }

    @Transactional
    public void updatePostV2(
            Long userId,
            Long postId,
            String content,
            List<UUID> mediaIds,
            List<MediaRevisionActivationRequest> revisionActivations
    ) {
        User user = getActiveUser(userId);
        Post post = getActivePost(postId);
        validatePostOwner(post, user);
        List<PostImage> previousImages = postImageRepository
                .findAllByAuthorPostOrderByDisplayOrderAsc(post);
        List<UUID> previousMediaIds = previousImages
                .stream()
                .map(PostImage::getMediaAsset)
                .filter(java.util.Objects::nonNull)
                .map(MediaAsset::getMediaId)
                .toList();
        boolean previousHasLegacyImage = previousImages.stream()
                .anyMatch(image -> image.getMediaAsset() == null);
        List<MediaAsset> media = mediaService.requireReadyMedia(
                userId, MediaPurpose.POST, mediaIds
        );
        validateRevisionActivations(previousMediaIds, mediaIds, revisionActivations);

        boolean contentChanged = !post.getContent().trim().equals(content.trim());
        boolean mediaChanged = previousHasLegacyImage || !previousMediaIds.equals(mediaIds);
        if (!contentChanged && !mediaChanged && revisionActivations.isEmpty()) {
            throw new CustomException(ErrorCode.NO_CHANGES_DETECTED);
        }

        revisionActivations.forEach(activation -> mediaRevisionService.activate(
                userId, activation.mediaId(), activation.revision()
        ));
        post.update(content);
        postImageRepository.deleteByAuthorPost(post);
        postImageRepository.flush();
        for (int index = 0; index < media.size(); index++) {
            MediaAsset asset = media.get(index);
            postImageRepository.save(new PostImage(
                    post,
                    mediaService.compatibilityUrl(asset),
                    asset,
                    index
            ));
        }
        postImageRepository.flush();
        java.util.Set<UUID> retained = new java.util.HashSet<>(mediaIds);
        previousMediaIds.stream()
                .filter(previousId -> !retained.contains(previousId))
                .forEach(previousId -> mediaService.deleteIfUnreferenced(userId, previousId));
    }

    @Transactional
    public Post updatePostAsyncMedia(
            Long userId,
            Long postId,
            String content,
            List<UUID> mediaIds,
            List<MediaRevisionTargetRequest> revisionTargets
    ) {
        User user = getActiveUser(userId);
        Post post = getActivePost(postId);
        validatePostOwner(post, user);
        List<PostImage> images = new java.util.ArrayList<>(postImageRepository
                .findAllByAuthorPostOrderByDisplayOrderAsc(post));
        List<MediaAsset> targetMedia = mediaService.requireAttachableMedia(
                userId, MediaPurpose.POST, mediaIds
        );
        java.util.Map<UUID, MediaRevisionTargetRequest> revisions = revisionTargets.stream()
                .collect(java.util.stream.Collectors.toMap(
                        MediaRevisionTargetRequest::mediaId,
                        activation -> activation,
                        (left, right) -> {
                            throw new CustomException(ErrorCode.INVALID_POST_REQUEST);
                        }
                ));
        if (!new java.util.HashSet<>(mediaIds).containsAll(revisions.keySet())) {
            throw new CustomException(ErrorCode.INVALID_POST_REQUEST);
        }
        Set<UUID> previousMediaIds = images.stream()
                .flatMap(image -> java.util.stream.Stream.of(
                        image.getMediaAsset(), image.getPendingMedia()
                ))
                .filter(java.util.Objects::nonNull)
                .map(MediaAsset::getMediaId)
                .collect(java.util.stream.Collectors.toSet());

        post.update(content);
        for (int index = 0; index < targetMedia.size(); index++) {
            MediaAsset asset = targetMedia.get(index);
            PostImage image = index < images.size()
                    ? images.get(index)
                    : PostImage.pending(post, asset, index, asset.getOperationId());
            MediaRevisionTargetRequest revisionTarget = revisions.get(asset.getMediaId());
            if (revisionTarget != null) {
                var revision = mediaRevisionService.requireAttachableRevision(
                        userId, asset.getMediaId(), revisionTarget.revision()
                );
                if (!revision.getOperationId().equals(revisionTarget.operationId())) {
                    throw new CustomException(ErrorCode.INVALID_POST_REQUEST);
                }
                if (revision.getStatus() == com.ian.community.common.media.MediaStatus.READY) {
                    mediaRevisionService.activate(userId, asset.getMediaId(), revision.getRevision());
                    image.replaceWithReady(mediaService.compatibilityUrl(asset), asset, index);
                } else {
                    image.requestRevision(
                            asset,
                            revision.getRevision(),
                            revisionTarget.operationId()
                    );
                }
            } else if (asset.getStatus() == com.ian.community.common.media.MediaStatus.READY) {
                image.replaceWithReady(mediaService.compatibilityUrl(asset), asset, index);
            } else {
                image.replaceWithPending(asset, index, asset.getOperationId());
            }
            if (index >= images.size()) {
                postImageRepository.save(image);
            }
        }
        if (images.size() > targetMedia.size()) {
            images.subList(targetMedia.size(), images.size()).forEach(postImageRepository::delete);
        }
        postImageRepository.flush();
        Set<UUID> retained = new java.util.HashSet<>(mediaIds);
        previousMediaIds.stream()
                .filter(mediaId -> !retained.contains(mediaId))
                .forEach(mediaId -> mediaService.deleteIfUnreferenced(userId, mediaId));
        return post;
    }

    private void validateRevisionActivations(
            List<UUID> previousMediaIds,
            List<UUID> nextMediaIds,
            List<MediaRevisionActivationRequest> activations
    ) {
        Set<UUID> seen = new java.util.HashSet<>();
        for (MediaRevisionActivationRequest activation : activations) {
            if (!seen.add(activation.mediaId())
                    || !previousMediaIds.contains(activation.mediaId())
                    || !nextMediaIds.contains(activation.mediaId())) {
                throw new CustomException(ErrorCode.INVALID_POST_REQUEST);
            }
        }
    }

    @Transactional
    public void deletePost(Long userId, Long postId) {
        User user = getActiveUser(userId);
        Post post = getActivePost(postId);

        validatePostOwner(post, user);

        if (post.isPostDeleted()) {
            throw new CustomException(ErrorCode.POST_ALREADY_DELETED);
        }

        post.delete();

        List<UUID> previousMediaIds = postImageRepository
                .findAllByAuthorPostOrderByDisplayOrderAsc(post)
                .stream()
                .map(PostImage::getMediaAsset)
                .filter(java.util.Objects::nonNull)
                .map(MediaAsset::getMediaId)
                .distinct()
                .toList();
        postImageRepository.deleteByAuthorPost(post);
        postImageRepository.flush();
        previousMediaIds.forEach(previousId -> mediaService.deleteIfUnreferenced(userId, previousId));
    }

    private void increaseViewCountIfAllowed(User user, Post post) {
        PostView postView = postViewRepository
                .findByAuthorUserAndAuthorPost(user, post)
                .orElse(null);

        if (postView == null) {
            PostView newPostView = new PostView(user, post);
            postViewRepository.save(newPostView);
            post.increaseViewCount();
            return;
        }

        LocalDateTime twentyFourHoursAgo = LocalDateTime.now().minusHours(24);

        if (postView.getViewedAt().isBefore(twentyFourHoursAgo)) {
            postView.updateViewedAt();
            post.increaseViewCount();
        }
    }

    private void updatePostImage(Post post, String imageUrl) {
        List<PostImage> images = postImageRepository
                .findAllByAuthorPostOrderByDisplayOrderAsc(post);
        PostImage postImage = images.isEmpty() ? null : images.getFirst();

        if (postImage == null) {
            PostImage newPostImage = new PostImage(post, imageUrl);
            postImageRepository.save(newPostImage);
            return;
        }

        List<UUID> previousMediaIds = images.stream()
                .map(PostImage::getMediaAsset)
                .filter(java.util.Objects::nonNull)
                .map(MediaAsset::getMediaId)
                .distinct()
                .toList();
        postImage.updateImageUrl(imageUrl);
        if (images.size() > 1) {
            images.subList(1, images.size()).forEach(postImageRepository::delete);
        }
        postImageRepository.flush();
        previousMediaIds.forEach(previousId -> mediaService.deleteIfUnreferenced(
                post.getAuthorUser().getUserId(), previousId
        ));
    }

    private User getActiveUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (user.isUserDeleted()) {
            throw new CustomException(ErrorCode.USER_ALREADY_DELETED);
        }

        return user;
    }

    private Post getActivePost(Long postId) {
        return postRepository.findByPostIdAndPostDeletedFalse(postId)
                .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));
    }

    private void validatePostOwner(Post post, User user) {
        if (!post.getAuthorUser().getUserId().equals(user.getUserId())) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
    }
}
