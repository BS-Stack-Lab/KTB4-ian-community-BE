package com.ian.community.common.media.worker;

import java.nio.file.Path;
import java.util.List;

public record TransformedMedia(
        Path masterPath,
        int masterWidth,
        int masterHeight,
        List<TransformedVariant> variants
) {}
