package com.ian.community.common.media;

import com.ian.community.common.exception.CustomException;
import com.ian.community.common.exception.ErrorCode;
import com.ian.community.common.media.dto.CropRectRequest;
import com.ian.community.common.media.dto.MediaPositionRequest;
import com.ian.community.common.media.dto.MediaRevisionRequest;
import com.ian.community.common.media.storage.MediaObjectStorage;
import com.ian.community.common.media.storage.PresignedGetObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MediaRevisionServiceTest {
    private MediaAssetRepository assetRepository;
    private MediaRevisionRepository revisionRepository;
    private MediaVariantRepository variantRepository;
    private MediaObjectStorage storage;
    private MediaRevisionService service;

    @BeforeEach
    void setUp() {
        assetRepository = mock(MediaAssetRepository.class);
        revisionRepository = mock(MediaRevisionRepository.class);
        variantRepository = mock(MediaVariantRepository.class);
        storage = mock(MediaObjectStorage.class);
        when(variantRepository.findAllByMediaAssetMediaIdAndMediaRevisionOrderByWidthAsc(
                any(), anyInt()
        )).thenReturn(List.of());
        service = new MediaRevisionService(
                assetRepository,
                revisionRepository,
                variantRepository,
                storage,
                properties()
        );
    }

    @Test
    void creatingRevisionIncrementsLatestWithoutChangingActiveRevision() {
        MediaAsset asset = readyAsset(1L);
        when(assetRepository.findByIdForUpdate(asset.getMediaId()))
                .thenReturn(Optional.of(asset));
        ArgumentCaptor<MediaRevision> revisionCaptor = ArgumentCaptor.forClass(MediaRevision.class);

        var response = service.create(1L, asset.getMediaId(), request("0.1", "0.2", "0.8", "0.6", "1.5"));

        verify(revisionRepository).save(revisionCaptor.capture());
        MediaRevision revision = revisionCaptor.getValue();
        assertEquals(2, response.revision());
        assertEquals(MediaStatus.UPLOADED, response.status());
        assertEquals(1, asset.getMediaRevision());
        assertEquals(2, asset.getLatestRevision());
        assertEquals(new BigDecimal("1.5"), revision.getZoom());
    }

    @Test
    void onlyReadyOwnedRevisionCanBecomeActive() {
        MediaAsset asset = readyAsset(1L);
        MediaRevision revision = revision(asset, 2, MediaStatus.UPLOADED);
        when(assetRepository.findByIdForUpdate(asset.getMediaId()))
                .thenReturn(Optional.of(asset));
        when(revisionRepository.findForUpdate(asset.getMediaId(), 2))
                .thenReturn(Optional.of(revision));

        CustomException notReady = assertThrows(
                CustomException.class,
                () -> service.activate(1L, asset.getMediaId(), 2)
        );
        assertEquals(ErrorCode.MEDIA_REVISION_NOT_READY, notReady.getErrorCode());
        assertEquals(1, asset.getMediaRevision());

        assertTrue(revision.claim(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(5)));
        revision.markReady();
        service.activate(1L, asset.getMediaId(), 2);

        assertEquals(2, asset.getMediaRevision());
        assertEquals(MediaFrame.POST_LANDSCAPE, asset.getFrame());
        assertNotNull(revision.getActivatedAt());
    }

    @Test
    void editSourceUsesPrivateMasterAndReturnsActiveEditState() {
        MediaAsset asset = readyAsset(1L);
        MediaRevision active = MediaRevision.initial(asset);
        when(assetRepository.findById(asset.getMediaId())).thenReturn(Optional.of(asset));
        when(revisionRepository.findByMediaAssetMediaIdAndRevision(asset.getMediaId(), 1))
                .thenReturn(Optional.of(active));
        when(storage.createDownload(eq("private/media/master.webp"), any()))
                .thenReturn(new PresignedGetObject(
                        "https://private.example/master.webp",
                        Instant.now().plusSeconds(600)
                ));

        var response = service.editSource(1L, asset.getMediaId());

        assertEquals("https://private.example/master.webp", response.url());
        assertEquals(1600, response.width());
        assertEquals(900, response.height());
        assertEquals(1, response.activeRevision());
        assertEquals(BigDecimal.ONE, response.zoom());
    }

    @Test
    void ownershipAndCropBoundsAreValidatedBeforeAllocatingRevision() {
        MediaAsset foreign = readyAsset(2L);
        when(assetRepository.findByIdForUpdate(foreign.getMediaId()))
                .thenReturn(Optional.of(foreign));
        CustomException ownership = assertThrows(
                CustomException.class,
                () -> service.create(1L, foreign.getMediaId(), request("0", "0", "1", "1", "1"))
        );
        assertEquals(ErrorCode.UPLOAD_NOT_OWNED, ownership.getErrorCode());

        MediaAsset owned = readyAsset(1L);
        when(assetRepository.findByIdForUpdate(owned.getMediaId()))
                .thenReturn(Optional.of(owned));
        CustomException crop = assertThrows(
                CustomException.class,
                () -> service.create(1L, owned.getMediaId(), request("0.8", "0", "0.3", "1", "1"))
        );
        assertEquals(ErrorCode.INVALID_CROP_RECT, crop.getErrorCode());
        assertEquals(1, owned.getLatestRevision());

        MediaRevisionRequest invalidPosition = new MediaRevisionRequest(
                MediaFrame.POST_LANDSCAPE,
                new CropRectRequest(
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE
                ),
                new BigDecimal("3.01"),
                new MediaPositionRequest(new BigDecimal("1.1"), new BigDecimal("0.5"))
        );
        CustomException editState = assertThrows(
                CustomException.class,
                () -> service.create(1L, owned.getMediaId(), invalidPosition)
        );
        assertEquals(ErrorCode.INVALID_CROP_RECT, editState.getErrorCode());
        assertEquals(1, owned.getLatestRevision());
    }

    private MediaRevisionRequest request(
            String x,
            String y,
            String width,
            String height,
            String zoom
    ) {
        BigDecimal cropX = new BigDecimal(x);
        BigDecimal cropY = new BigDecimal(y);
        BigDecimal cropWidth = new BigDecimal(width);
        BigDecimal cropHeight = new BigDecimal(height);
        return new MediaRevisionRequest(
                MediaFrame.POST_LANDSCAPE,
                new CropRectRequest(cropX, cropY, cropWidth, cropHeight),
                new BigDecimal(zoom),
                new MediaPositionRequest(
                        cropX.add(cropWidth.divide(BigDecimal.valueOf(2))),
                        cropY.add(cropHeight.divide(BigDecimal.valueOf(2)))
                )
        );
    }

    private MediaAsset readyAsset(Long owner) {
        MediaAsset asset = new MediaAsset(
                owner,
                MediaPurpose.POST,
                MediaFrame.POST_LANDSCAPE,
                0,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ONE,
                BigDecimal.ONE,
                "image/jpeg",
                1024,
                1
        );
        asset.markReady("private/media/master.webp", 1600, 900);
        return asset;
    }

    private MediaRevision revision(MediaAsset asset, int revision, MediaStatus status) {
        return new MediaRevision(
                asset,
                revision,
                status,
                MediaFrame.POST_LANDSCAPE,
                0,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                new BigDecimal("0.5"),
                new BigDecimal("0.5"),
                1
        );
    }

    private MediaProperties properties() {
        return new MediaProperties(
                true,
                "ap-northeast-2",
                "bucket",
                "queue",
                "role",
                "https://cdn.example",
                "distribution",
                "test",
                1,
                600,
                new MediaProperties.Worker("/tmp/media", 20, 300, 5)
        );
    }
}
