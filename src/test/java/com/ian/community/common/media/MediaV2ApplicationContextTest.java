package com.ian.community.common.media;

import com.ian.community.common.media.storage.S3PresignedPostFactory;
import com.ian.community.common.media.worker.SqsMediaJobPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(properties = {
        "app.runtime=api",
        "app.media.enabled=true",
        "app.media.aws-region=ap-northeast-2",
        "app.media.bucket=community-media-test",
        "app.media.queue-url=https://sqs.ap-northeast-2.amazonaws.com/000000000000/community-media-test",
        "app.media.role-arn=",
        "app.media.cdn-base-url=https://example.cloudfront.net",
        "app.media.distribution-id=E0000000000000",
        "app.media.environment-name=test"
})
class MediaV2ApplicationContextTest {
    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void mediaV2ApiContextUsesTheAutoConfiguredJackson3Mapper() {
        assertNotNull(applicationContext.getBean(JsonMapper.class));
        assertNotNull(applicationContext.getBean(S3PresignedPostFactory.class));
        assertNotNull(applicationContext.getBean(SqsMediaJobPublisher.class));
    }
}
