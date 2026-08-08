package com.ian.community.common.media;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.media")
public record MediaProperties(
        boolean enabled,
        String awsRegion,
        String bucket,
        String queueUrl,
        String roleArn,
        String cdnBaseUrl,
        String distributionId,
        String environmentName,
        int transformVersion,
        long uploadExpirationSeconds,
        Worker worker
) {
    public record Worker(
            String scratchRoot,
            int waitTimeSeconds,
            int visibilityTimeoutSeconds,
            int maxReceiveCount
    ) {}
}
