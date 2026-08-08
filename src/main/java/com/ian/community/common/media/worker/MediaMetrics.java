package com.ian.community.common.media.worker;

import com.ian.community.common.media.MediaProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.Dimension;
import software.amazon.awssdk.services.cloudwatch.model.MetricDatum;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataRequest;
import software.amazon.awssdk.services.cloudwatch.model.StandardUnit;

@Component
@ConditionalOnProperty(name = "app.runtime", havingValue = "worker")
public class MediaMetrics {
    private final CloudWatchClient cloudWatchClient;
    private final MediaProperties properties;

    public MediaMetrics(CloudWatchClient cloudWatchClient, MediaProperties properties) {
        this.cloudWatchClient = cloudWatchClient;
        this.properties = properties;
    }

    public void recordProcessingFailure() {
        try {
            cloudWatchClient.putMetricData(PutMetricDataRequest.builder()
                    .namespace("Community/MediaV2")
                    .metricData(MetricDatum.builder()
                            .metricName("ProcessingFailures")
                            .unit(StandardUnit.COUNT)
                            .value(1.0)
                            .dimensions(Dimension.builder()
                                    .name("Environment")
                                    .value(properties.environmentName())
                                    .build())
                            .build())
                    .build());
        } catch (RuntimeException ignored) {
            // A metric delivery failure must not change SQS retry semantics.
        }
    }
}
