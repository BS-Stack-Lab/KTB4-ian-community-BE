package com.ian.community.common.media.config;

import com.ian.community.common.media.MediaProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudfront.CloudFrontClient;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;

@Configuration
@ConditionalOnProperty(name = "app.media.enabled", havingValue = "true")
public class AwsMediaConfiguration {
    @Bean(destroyMethod = "close")
    public StsClient mediaStsClient(MediaProperties properties) {
        return StsClient.builder()
                .region(Region.of(properties.awsRegion()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Bean(destroyMethod = "close")
    public AwsCredentialsProvider mediaCredentialsProvider(
            MediaProperties properties,
            StsClient mediaStsClient
    ) {
        if (properties.roleArn() == null || properties.roleArn().isBlank()) {
            return DefaultCredentialsProvider.create();
        }
        return StsAssumeRoleCredentialsProvider.builder()
                .stsClient(mediaStsClient)
                .refreshRequest(AssumeRoleRequest.builder()
                        .roleArn(properties.roleArn())
                        .roleSessionName("community-" + System.getenv().getOrDefault("APP_RUNTIME", "api"))
                        .build())
                .build();
    }

    @Bean(destroyMethod = "close")
    public S3Client mediaS3Client(MediaProperties properties, AwsCredentialsProvider credentialsProvider) {
        return S3Client.builder()
                .region(Region.of(properties.awsRegion()))
                .credentialsProvider(credentialsProvider)
                .build();
    }

    @Bean(destroyMethod = "close")
    public S3Presigner mediaS3Presigner(
            MediaProperties properties,
            AwsCredentialsProvider credentialsProvider
    ) {
        return S3Presigner.builder()
                .region(Region.of(properties.awsRegion()))
                .credentialsProvider(credentialsProvider)
                .build();
    }

    @Bean(destroyMethod = "close")
    public SqsClient mediaSqsClient(MediaProperties properties, AwsCredentialsProvider credentialsProvider) {
        return SqsClient.builder()
                .region(Region.of(properties.awsRegion()))
                .credentialsProvider(credentialsProvider)
                .build();
    }

    @Bean(destroyMethod = "close")
    public CloudFrontClient mediaCloudFrontClient(AwsCredentialsProvider credentialsProvider) {
        return CloudFrontClient.builder()
                .region(Region.AWS_GLOBAL)
                .credentialsProvider(credentialsProvider)
                .build();
    }

    @Bean(destroyMethod = "close")
    public CloudWatchClient mediaCloudWatchClient(
            MediaProperties properties,
            AwsCredentialsProvider credentialsProvider
    ) {
        return CloudWatchClient.builder()
                .region(Region.of(properties.awsRegion()))
                .credentialsProvider(credentialsProvider)
                .build();
    }
}
