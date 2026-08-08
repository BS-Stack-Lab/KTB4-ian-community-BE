package com.ian.community.common.media.worker;

import com.ian.community.common.media.MediaVariantType;

import java.nio.file.Path;

public record TransformedVariant(MediaVariantType type, Path path, long fileSize) {}
