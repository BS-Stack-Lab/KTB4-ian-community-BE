package com.ian.community.common.media;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MediaVariantPolicyTest {
    @Test
    void postsGenerateStandardAndHighResolutionVariants() {
        assertEquals(
                List.of(
                        MediaVariantType.POST_PORTRAIT_1X,
                        MediaVariantType.POST_PORTRAIT_3X
                ),
                MediaVariantPolicy.generatedFor(MediaFrame.POST_PORTRAIT)
        );
        assertEquals(
                List.of(
                        MediaVariantType.POST_LANDSCAPE_1X,
                        MediaVariantType.POST_LANDSCAPE_3X
                ),
                MediaVariantPolicy.generatedFor(MediaFrame.POST_LANDSCAPE)
        );
        assertEquals(
                List.of(
                        MediaVariantType.PROFILE_SMALL,
                        MediaVariantType.PROFILE_MEDIUM,
                        MediaVariantType.PROFILE_LARGE
                ),
                MediaVariantPolicy.generatedFor(MediaFrame.PROFILE)
        );
    }

    @Test
    void postResponsesExposeStandardAndHighResolutionWithoutTheRetiredTwoX() {
        MediaAsset asset = postAsset();
        MediaRevision revision = MediaRevision.initial(asset);
        MediaVariant small = variant(
                asset, revision, MediaVariantType.POST_LANDSCAPE_1X
        );
        MediaVariant medium = variant(
                asset, revision, MediaVariantType.POST_LANDSCAPE_2X
        );
        MediaVariant large = variant(
                asset, revision, MediaVariantType.POST_LANDSCAPE_3X
        );

        assertEquals(
                List.of(small, large),
                MediaVariantPolicy.responseVariants(
                        MediaFrame.POST_LANDSCAPE,
                        List.of(small, medium, large)
                )
        );
        assertEquals(
                List.of(small),
                MediaVariantPolicy.responseVariants(
                        MediaFrame.POST_LANDSCAPE,
                        List.of(small, medium)
                )
        );
        assertEquals(
                List.of(medium),
                MediaVariantPolicy.responseVariants(
                        MediaFrame.POST_LANDSCAPE,
                        List.of(medium)
                )
        );
    }

    private MediaAsset postAsset() {
        return new MediaAsset(
                1L,
                MediaPurpose.POST,
                MediaFrame.POST_LANDSCAPE,
                0,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ONE,
                BigDecimal.ONE,
                "image/jpeg",
                1024,
                1
        );
    }

    private MediaVariant variant(
            MediaAsset asset,
            MediaRevision revision,
            MediaVariantType type
    ) {
        return new MediaVariant(
                asset,
                revision,
                type,
                "public/media/test/" + type.getKeyName() + ".webp",
                1024
        );
    }
}
