package com.ian.community.common.media.storage;

import java.time.Instant;

public record PresignedGetObject(String url, Instant expiresAt) {}
