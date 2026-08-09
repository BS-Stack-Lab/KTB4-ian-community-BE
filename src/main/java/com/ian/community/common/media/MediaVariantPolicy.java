package com.ian.community.common.media;

import java.util.Comparator;
import java.util.List;

public final class MediaVariantPolicy {
    private MediaVariantPolicy() {
    }

    public static List<MediaVariantType> generatedFor(MediaFrame frame) {
        return switch (frame) {
            case PROFILE -> List.of(
                    MediaVariantType.PROFILE_SMALL,
                    MediaVariantType.PROFILE_MEDIUM,
                    MediaVariantType.PROFILE_LARGE
            );
            case POST_PORTRAIT -> List.of(MediaVariantType.POST_PORTRAIT_3X);
            case POST_LANDSCAPE -> List.of(MediaVariantType.POST_LANDSCAPE_3X);
        };
    }

    public static List<MediaVariant> responseVariants(
            MediaFrame frame,
            List<MediaVariant> variants
    ) {
        if (frame == MediaFrame.PROFILE || variants.isEmpty()) {
            return variants;
        }
        MediaVariantType preferred = preferredFor(frame);
        return variants.stream()
                .filter(variant -> variant.getVariantType() == preferred)
                .findFirst()
                .map(List::of)
                .orElseGet(() -> variants.stream()
                        .max(Comparator.comparingInt(MediaVariant::getWidth))
                        .map(List::of)
                        .orElseGet(List::of));
    }

    public static MediaVariantType preferredFor(MediaFrame frame) {
        return switch (frame) {
            case PROFILE -> MediaVariantType.PROFILE_MEDIUM;
            case POST_PORTRAIT -> MediaVariantType.POST_PORTRAIT_3X;
            case POST_LANDSCAPE -> MediaVariantType.POST_LANDSCAPE_3X;
        };
    }
}
