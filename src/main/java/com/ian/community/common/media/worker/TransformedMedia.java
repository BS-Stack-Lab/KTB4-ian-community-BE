package com.ian.community.common.media.worker;

import com.ian.community.common.media.MediaQualityLevel;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;

public record TransformedMedia(
        Path masterPath,
        int masterWidth,
        int masterHeight,
        String sourceFormat,
        MediaOutputFormat outputFormat,
        int cropPixelWidth,
        int cropPixelHeight,
        MediaQualityLevel qualityLevel,
        BigDecimal upscaleRatio1x,
        List<TransformedVariant> variants
) {}
