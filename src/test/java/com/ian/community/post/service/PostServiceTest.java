package com.ian.community.post.service;

import com.ian.community.common.exception.CustomException;
import com.ian.community.common.exception.ErrorCode;
import com.ian.community.common.media.MediaAsset;
import com.ian.community.common.media.MediaStatus;
import com.ian.community.common.media.MediaRevisionService;
import com.ian.community.common.media.MediaService;
import com.ian.community.common.media.dto.MediaRevisionActivationRequest;
import com.ian.community.post.domain.Post;
import com.ian.community.post.domain.PostImage;
import com.ian.community.post.domain.PostImageMediaState;
import com.ian.community.post.repository.PostImageRepository;
import com.ian.community.post.repository.PostRepository;
import com.ian.community.post.repository.PostViewRepository;
import com.ian.community.user.domain.User;
import com.ian.community.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PostServiceTest {
    private UserRepository userRepository;
    private PostRepository postRepository;
    private PostImageRepository imageRepository;
    private MediaService mediaService;
    private MediaRevisionService revisionService;
    private PostService service;
    private User user;
    private Post post;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        postRepository = mock(PostRepository.class);
        imageRepository = mock(PostImageRepository.class);
        mediaService = mock(MediaService.class);
        revisionService = mock(MediaRevisionService.class);
        service = new PostService(
                userRepository,
                postRepository,
                imageRepository,
                mock(PostViewRepository.class),
                mediaService,
                revisionService
        );
        user = mock(User.class);
        post = mock(Post.class);
        when(user.getUserId()).thenReturn(1L);
        when(user.isUserDeleted()).thenReturn(false);
        when(post.getAuthorUser()).thenReturn(user);
        when(post.getContent()).thenReturn("기존 본문");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.findByPostIdAndPostDeletedFalse(31L)).thenReturn(Optional.of(post));
    }

    @Test
    void imageAdditionAloneCanUpdatePostWhileBodyRemainsRequired() {
        UUID mediaId = UUID.randomUUID();
        MediaAsset media = mock(MediaAsset.class);
        when(media.getMediaId()).thenReturn(mediaId);
        when(imageRepository.findAllByAuthorPostOrderByDisplayOrderAsc(post))
                .thenReturn(List.of());
        when(mediaService.requireReadyMedia(1L, com.ian.community.common.media.MediaPurpose.POST, List.of(mediaId)))
                .thenReturn(List.of(media));
        when(mediaService.compatibilityUrl(media)).thenReturn("https://cdn.example/new.webp");

        service.updatePostV2(1L, 31L, "기존 본문", List.of(mediaId), List.of());

        verify(post).update("기존 본문");
        verify(imageRepository).save(any(PostImage.class));
    }

    @Test
    void readyRevisionIsActivatedOnlyAsPartOfPostUpdate() {
        UUID mediaId = UUID.randomUUID();
        MediaAsset media = mock(MediaAsset.class);
        PostImage image = mock(PostImage.class);
        when(media.getMediaId()).thenReturn(mediaId);
        when(image.getMediaAsset()).thenReturn(media);
        when(imageRepository.findAllByAuthorPostOrderByDisplayOrderAsc(post))
                .thenReturn(List.of(image));
        when(mediaService.requireReadyMedia(1L, com.ian.community.common.media.MediaPurpose.POST, List.of(mediaId)))
                .thenReturn(List.of(media));
        when(mediaService.compatibilityUrl(media)).thenReturn("https://cdn.example/revision.webp");

        service.updatePostV2(
                1L,
                31L,
                "기존 본문",
                List.of(mediaId),
                List.of(new MediaRevisionActivationRequest(mediaId, 2))
        );

        verify(revisionService).activate(1L, mediaId, 2);
        verify(post).update("기존 본문");
    }

    @Test
    void legacyImageRemovalAloneIsDetectedAsAChange() {
        PostImage legacyImage = mock(PostImage.class);
        when(legacyImage.getMediaAsset()).thenReturn(null);
        when(imageRepository.findAllByAuthorPostOrderByDisplayOrderAsc(post))
                .thenReturn(List.of(legacyImage));
        when(mediaService.requireReadyMedia(1L, com.ian.community.common.media.MediaPurpose.POST, List.of()))
                .thenReturn(List.of());

        service.updatePostV2(1L, 31L, "기존 본문", List.of(), List.of());

        verify(imageRepository).deleteByAuthorPost(post);
        verify(post).update("기존 본문");
    }

    @Test
    void unchangedBodyMediaAndRevisionAreRejected() {
        UUID mediaId = UUID.randomUUID();
        MediaAsset media = mock(MediaAsset.class);
        PostImage image = mock(PostImage.class);
        when(media.getMediaId()).thenReturn(mediaId);
        when(image.getMediaAsset()).thenReturn(media);
        when(imageRepository.findAllByAuthorPostOrderByDisplayOrderAsc(post))
                .thenReturn(List.of(image));
        when(mediaService.requireReadyMedia(1L, com.ian.community.common.media.MediaPurpose.POST, List.of(mediaId)))
                .thenReturn(List.of(media));

        CustomException exception = assertThrows(
                CustomException.class,
                () -> service.updatePostV2(
                        1L, 31L, " 기존 본문 ", List.of(mediaId), List.of()
                )
        );

        assertEquals(ErrorCode.NO_CHANGES_DETECTED, exception.getErrorCode());
        verify(revisionService, never()).activate(any(), any(), anyInt());
    }

    @Test
    void asyncCreateStoresUploadedMediaAsProcessingAttachment() {
        MediaAsset media = mock(MediaAsset.class);
        UUID mediaId = UUID.randomUUID();
        when(media.getMediaId()).thenReturn(mediaId);
        when(media.getStatus()).thenReturn(MediaStatus.UPLOADED);
        when(mediaService.requireAttachableMedia(
                1L,
                com.ian.community.common.media.MediaPurpose.POST,
                List.of(mediaId)
        )).thenReturn(List.of(media));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.createPostAsyncMedia(1L, "본문", List.of(mediaId));

        ArgumentCaptor<PostImage> image = ArgumentCaptor.forClass(PostImage.class);
        verify(imageRepository).save(image.capture());
        assertEquals(PostImageMediaState.PROCESSING, image.getValue().getMediaState());
        assertEquals(mediaId, image.getValue().getPendingMedia().getMediaId());
    }
}
