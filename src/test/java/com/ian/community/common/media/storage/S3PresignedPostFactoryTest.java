package com.ian.community.common.media.storage;

import com.ian.community.common.media.MediaProperties;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class S3PresignedPostFactoryTest {
    @Test
    void usesThePublicPathStyleEndpointForBrowserUploads() throws Exception {
        MediaProperties properties = properties(new MediaProperties.Endpoints(
                "http://localstack:4566",
                "http://127.0.0.1:14566/",
                "http://localstack:4566",
                "http://localstack:4566",
                true
        ));
        var factory = new S3PresignedPostFactory(
                properties,
                StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")),
                JsonMapper.builder().build()
        );

        var response = factory.create(
                "private/uploads/example.jpg",
                "image/jpeg",
                1024,
                Duration.ofMinutes(10)
        );

        assertEquals("http://127.0.0.1:14566/community-media-test", response.url());
        String policyJson = new String(
                Base64.getDecoder().decode(response.fields().get("policy")),
                StandardCharsets.UTF_8
        );
        String expiration = JsonMapper.builder().build().readTree(policyJson)
                .path("expiration")
                .asText();
        assertEquals(0, Instant.parse(expiration).getNano());
        assertFalse(expiration.contains("."));
    }

    @Test
    void keepsTheAwsUploadEndpointWhenOverridesAreEmpty() {
        MediaProperties properties = properties(
                new MediaProperties.Endpoints("", "", "", "", false)
        );
        var factory = new S3PresignedPostFactory(
                properties,
                StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")),
                JsonMapper.builder().build()
        );

        var response = factory.create(
                "private/uploads/example.jpg",
                "image/jpeg",
                1024,
                Duration.ofMinutes(10)
        );

        assertEquals(
                "https://community-media-test.s3.ap-northeast-2.amazonaws.com",
                response.url()
        );
    }

    private MediaProperties properties(MediaProperties.Endpoints endpoints) {
        return new MediaProperties(
                true,
                "ap-northeast-2",
                "community-media-test",
                "queue",
                "",
                endpoints,
                "https://cdn.example",
                "",
                "test",
                1,
                600,
                new MediaProperties.Worker("/tmp/media", 20, 300, 5)
        );
    }
}
