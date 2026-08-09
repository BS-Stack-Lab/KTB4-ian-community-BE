package com.ian.community.common.media.worker;

import com.ian.community.common.exception.ErrorCode;
import com.ian.community.common.media.MediaAsset;
import com.ian.community.common.media.MediaProperties;
import com.ian.community.common.media.MediaRevision;
import com.ian.community.common.media.storage.MediaObjectStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MediaProcessingServiceRetryTest {
    @TempDir
    Path scratch;

    @Test
    void transientStorageFailureReleasesTheLeaseForTheNextSqsDelivery() {
        Fixture fixture = fixture();
        doThrow(new IllegalStateException("temporary S3 failure"))
                .when(fixture.storage).download(any(), any());

        assertThrows(
                IllegalStateException.class,
                () -> fixture.service.process(fixture.mediaId, 1)
        );

        verify(fixture.transactions).releaseForRetry(fixture.mediaId, 1);
        verify(fixture.transactions, never()).fail(any(), anyInt(), any());
    }

    @Test
    void permanentImageFailureStaysFailedAndDoesNotReturnToTheQueueState() {
        Fixture fixture = fixture();
        doThrow(new PermanentMediaProcessingException(ErrorCode.CORRUPTED_IMAGE))
                .when(fixture.engine).transform(any(), any(), any());

        assertThrows(
                PermanentMediaProcessingException.class,
                () -> fixture.service.process(fixture.mediaId, 1)
        );

        verify(fixture.transactions).fail(
                fixture.mediaId, 1, ErrorCode.CORRUPTED_IMAGE
        );
        verify(fixture.transactions, never()).releaseForRetry(any(), anyInt());
    }

    private Fixture fixture() {
        UUID mediaId = UUID.randomUUID();
        MediaAsset asset = mock(MediaAsset.class);
        MediaRevision revision = mock(MediaRevision.class);
        MediaProcessingTransactions transactions = mock(MediaProcessingTransactions.class);
        MediaObjectStorage storage = mock(MediaObjectStorage.class);
        ImageTransformEngine engine = mock(ImageTransformEngine.class);
        MediaMetrics metrics = mock(MediaMetrics.class);
        when(asset.getMediaId()).thenReturn(mediaId);
        when(asset.getSourceKey()).thenReturn("private/uploads/" + mediaId + "/source");
        when(asset.getMasterKey()).thenReturn(null);
        when(revision.getTransformVersion()).thenReturn(1);
        when(transactions.claim(mediaId, 1))
                .thenReturn(new MediaProcessingTransactions.ProcessingClaim(asset, revision));
        MediaProcessingService service = new MediaProcessingService(
                transactions,
                storage,
                engine,
                properties(),
                metrics
        );
        return new Fixture(mediaId, transactions, storage, engine, service);
    }

    private MediaProperties properties() {
        return new MediaProperties(
                true,
                "ap-northeast-2",
                "bucket",
                "queue",
                "",
                new MediaProperties.Endpoints("", "", "", "", false),
                "https://cdn.example",
                "",
                "test",
                1,
                600,
                new MediaProperties.Worker(scratch.toString(), 1, 2, 3)
        );
    }

    private record Fixture(
            UUID mediaId,
            MediaProcessingTransactions transactions,
            MediaObjectStorage storage,
            ImageTransformEngine engine,
            MediaProcessingService service
    ) {}
}
