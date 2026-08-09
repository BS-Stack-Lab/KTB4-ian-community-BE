package com.ian.community.common.media.worker;

import com.ian.community.common.media.MediaProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.awssdk.services.sqs.SqsClient;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class SqsMediaWorkerTest {
    @TempDir
    Path scratch;

    @Test
    void duplicateS3EventsResolveToTheSameDeterministicMediaId() throws Exception {
        UUID mediaId = UUID.randomUUID();
        SqsMediaWorker worker = worker();
        String event = """
                {"Records":[{"s3":{"object":{"key":"private/uploads/%s/source"}}}]}
                """.formatted(mediaId);

        assertEquals(mediaId, worker.extractMediaId(event));
        assertEquals(mediaId, worker.extractMediaId(event));
    }

    @Test
    void revisionMessagesKeepMediaIdAndRevisionForIdempotentProcessing() throws Exception {
        UUID mediaId = UUID.randomUUID();
        SqsMediaWorker.MediaJob job = worker().extractJob("""
                {"type":"MEDIA_REVISION","mediaId":"%s","revision":3}
                """.formatted(mediaId));

        assertEquals(mediaId, job.mediaId());
        assertEquals(3, job.revision());
    }

    @Test
    void startupSweepRemovesOnlyStaleMediaJobDirectories() throws Exception {
        Path stale = Files.createDirectories(scratch.resolve("media-stale-job"));
        Files.writeString(stale.resolve("source"), "temporary");
        Path heartbeat = Files.writeString(scratch.resolve("heartbeat"), "keep");

        worker().sweepScratch();

        assertFalse(Files.exists(stale));
        assertTrue(Files.exists(heartbeat));
    }

    private SqsMediaWorker worker() {
        MediaProperties properties = new MediaProperties(
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
                new MediaProperties.Worker(scratch.toString(), 20, 300, 5)
        );
        return new SqsMediaWorker(
                mock(SqsClient.class),
                mock(MediaProcessingService.class),
                properties,
                JsonMapper.builder().build()
        );
    }
}
