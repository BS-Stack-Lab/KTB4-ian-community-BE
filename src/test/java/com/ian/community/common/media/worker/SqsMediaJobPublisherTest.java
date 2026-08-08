package com.ian.community.common.media.worker;

import com.ian.community.common.media.MediaProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SqsMediaJobPublisherTest {
    @Test
    void publishesARevisionMessageThatTheJackson3WorkerCanRead() {
        SqsClient sqsClient = mock(SqsClient.class);
        JsonMapper jsonMapper = JsonMapper.builder().build();
        MediaProperties properties = properties();
        SqsMediaJobPublisher publisher = new SqsMediaJobPublisher(sqsClient, properties, jsonMapper);
        UUID mediaId = UUID.randomUUID();

        publisher.publishRevision(mediaId, 3);

        ArgumentCaptor<SendMessageRequest> request = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(sqsClient).sendMessage(request.capture());
        JsonNode body = jsonMapper.readTree(request.getValue().messageBody());
        assertEquals(properties.queueUrl(), request.getValue().queueUrl());
        assertEquals("MEDIA_REVISION", body.path("type").asText());
        assertEquals(mediaId, UUID.fromString(body.path("mediaId").asText()));
        assertEquals(3, body.path("revision").asInt());
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
                new MediaProperties.Worker("/tmp/media-worker-test", 20, 300, 5)
        );
    }
}
