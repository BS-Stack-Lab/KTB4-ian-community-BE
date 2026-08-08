package com.ian.community.common.media.worker;

import com.ian.community.common.media.MediaProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.media.enabled", havingValue = "true")
public class SqsMediaJobPublisher implements MediaJobPublisher {
    private final SqsClient sqsClient;
    private final MediaProperties properties;
    private final JsonMapper jsonMapper;

    @Override
    public void publishRevision(UUID mediaId, int revision) {
        try {
            String body = jsonMapper.writeValueAsString(new RevisionJob(
                    "MEDIA_REVISION", mediaId, revision
            ));
            sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(properties.queueUrl())
                    .messageBody(body)
                    .build());
        } catch (JacksonException exception) {
            throw new IllegalStateException("Unable to serialize media revision job", exception);
        }
    }

    private record RevisionJob(String type, UUID mediaId, int revision) {}
}
