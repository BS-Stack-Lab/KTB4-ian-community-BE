package com.ian.community.common.media.config;

import com.ian.community.common.media.MediaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableConfigurationProperties(MediaProperties.class)
@EnableScheduling
public class MediaConfiguration {}
