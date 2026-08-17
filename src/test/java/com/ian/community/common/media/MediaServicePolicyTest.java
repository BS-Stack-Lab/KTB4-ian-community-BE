package com.ian.community.common.media;

import com.ian.community.common.exception.CustomException;
import com.ian.community.common.exception.ErrorCode;
import com.ian.community.common.media.dto.CropRectRequest;
import com.ian.community.common.media.dto.MediaUploadRequest;
import com.ian.community.common.media.dto.PresignedPostResponse;
import com.ian.community.common.media.storage.MediaObjectStorage;
import com.ian.community.post.repository.PostImageRepository;
import com.ian.community.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MediaServicePolicyTest {
    private MediaAssetRepository assetRepository;
    private MediaObjectStorage storage;
    private MediaService service;

    @BeforeEach
    void setUp() {
        assetRepository = mock(MediaAssetRepository.class);
        storage = mock(MediaObjectStorage.class);
        service = new MediaService(
                assetRepository,
                mock(MediaVariantRepository.class),
                mock(MediaRevisionRepository.class),
                storage,
                new MediaProperties(
                        true,
                        "ap-northeast-2",
                        "bucket",
                        "queue",
                        "role",
                        new MediaProperties.Endpoints("", "", "", "", false),
                        "https://cdn.example",
                        "distribution",
                        "test",
                        1,
                        600,
                        new MediaProperties.Worker("/tmp/media", 20, 300, 5)
                ),
                mock(PostImageRepository.class),
                mock(UserRepository.class)
        );
        when(storage.createUpload(anyString(), anyString(), anyLong(), any()))
                .thenReturn(new PresignedPostResponse(
                        "https://upload.example", Map.of("key", "source"), Instant.now().plusSeconds(600)
                ));
    }

    @Test
    void profileAndPostV2UploadLimitsAreEnforcedIndependently() {
        CustomException profileTooLarge = assertThrows(
                CustomException.class,
                () -> service.initiateUpload(1L, request(
                        MediaPurpose.PROFILE, MediaFrame.PROFILE, "profile.png", "image/png", 1024L * 1024L + 1
                ))
        );
        assertEquals(ErrorCode.IMAGE_TOO_LARGE, profileTooLarge.getErrorCode());

        service.initiateUpload(1L, request(
                MediaPurpose.POST,
                MediaFrame.POST_LANDSCAPE,
                "post.webp",
                "image/webp",
                10L * 1024L * 1024L
        ));
        verify(storage).createUpload(anyString(), eq("image/webp"), eq(10L * 1024L * 1024L), any());
    }

    @Test
    void extensionAndMimePairRejectsGifSvgAndMismatches() {
        for (MediaUploadRequest request : List.of(
                request(MediaPurpose.POST, MediaFrame.POST_LANDSCAPE, "image.gif", "image/gif", 100),
                request(MediaPurpose.POST, MediaFrame.POST_LANDSCAPE, "image.svg", "image/svg+xml", 100),
                request(MediaPurpose.POST, MediaFrame.POST_LANDSCAPE, "image.png", "image/jpeg", 100)
        )) {
            CustomException exception = assertThrows(
                    CustomException.class,
                    () -> service.initiateUpload(1L, request)
            );
            assertEquals(ErrorCode.UNSUPPORTED_IMAGE_TYPE, exception.getErrorCode());
        }
    }

    @Test
    void bmpIsAcceptedAsAReadOnlyFallbackInput() {
        service.initiateUpload(1L, request(
                MediaPurpose.POST,
                MediaFrame.POST_PORTRAIT,
                "camera.bmp",
                "image/x-ms-bmp",
                1024
        ));

        verify(storage).createUpload(
                anyString(), eq("image/bmp"), eq(10L * 1024L * 1024L), any()
        );
    }

    @Test
    void postMediaMustBeUniqueReadyOwnedAndLockedForAttachment() {
        MediaAsset ready = asset(1L, MediaPurpose.POST, MediaFrame.POST_LANDSCAPE);
        ready.markUploaded();
        ready.markReady("master", 896, 576);
        when(assetRepository.findByIdForUpdate(ready.getMediaId())).thenReturn(Optional.of(ready));

        assertEquals(
                List.of(ready),
                service.requireReadyMedia(1L, MediaPurpose.POST, List.of(ready.getMediaId()))
        );
        verify(assetRepository).findByIdForUpdate(ready.getMediaId());

        CustomException duplicate = assertThrows(
                CustomException.class,
                () -> service.requireReadyMedia(
                        1L, MediaPurpose.POST, List.of(ready.getMediaId(), ready.getMediaId())
                )
        );
        assertEquals(ErrorCode.INVALID_POST_REQUEST, duplicate.getErrorCode());
    }

    @Test
    void ownershipPurposeAndReadyStateCannotBeBypassed() {
        MediaAsset foreign = asset(2L, MediaPurpose.POST, MediaFrame.POST_LANDSCAPE);
        when(assetRepository.findByIdForUpdate(foreign.getMediaId())).thenReturn(Optional.of(foreign));
        assertCode(
                ErrorCode.UPLOAD_NOT_OWNED,
                () -> service.requireReadyMedia(1L, MediaPurpose.POST, List.of(foreign.getMediaId()))
        );

        MediaAsset pending = asset(1L, MediaPurpose.POST, MediaFrame.POST_LANDSCAPE);
        when(assetRepository.findByIdForUpdate(pending.getMediaId())).thenReturn(Optional.of(pending));
        assertCode(
                ErrorCode.MEDIA_NOT_READY,
                () -> service.requireReadyMedia(1L, MediaPurpose.POST, List.of(pending.getMediaId()))
        );

        MediaAsset profile = asset(1L, MediaPurpose.PROFILE, MediaFrame.PROFILE);
        profile.markUploaded();
        profile.markReady("master", 320, 320);
        when(assetRepository.findByIdForUpdate(profile.getMediaId())).thenReturn(Optional.of(profile));
        assertCode(
                ErrorCode.MEDIA_PURPOSE_MISMATCH,
                () -> service.requireReadyMedia(1L, MediaPurpose.POST, List.of(profile.getMediaId()))
        );
    }

    @Test
    void asyncAttachmentAcceptsUploadedButSynchronousAttachmentDoesNot() {
        MediaAsset uploaded = asset(1L, MediaPurpose.POST, MediaFrame.POST_LANDSCAPE);
        uploaded.markUploaded();
        when(assetRepository.findByIdForUpdate(uploaded.getMediaId()))
                .thenReturn(Optional.of(uploaded));

        assertEquals(
                List.of(uploaded),
                service.requireAttachableMedia(
                        1L, MediaPurpose.POST, List.of(uploaded.getMediaId())
                )
        );
        assertCode(
                ErrorCode.MEDIA_NOT_READY,
                () -> service.requireReadyMedia(
                        1L, MediaPurpose.POST, List.of(uploaded.getMediaId())
                )
        );
    }

    private void assertCode(ErrorCode errorCode, Runnable runnable) {
        CustomException exception = assertThrows(CustomException.class, runnable::run);
        assertEquals(errorCode, exception.getErrorCode());
    }

    private MediaUploadRequest request(
            MediaPurpose purpose,
            MediaFrame frame,
            String name,
            String contentType,
            long size
    ) {
        return new MediaUploadRequest(
                purpose,
                name,
                contentType,
                size,
                frame,
                0,
                new CropRectRequest(
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE
                )
        );
    }

    private MediaAsset asset(Long owner, MediaPurpose purpose, MediaFrame frame) {
        return new MediaAsset(
                owner,
                purpose,
                frame,
                0,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ONE,
                BigDecimal.ONE,
                "image/jpeg",
                1024,
                1
        );
    }
}
