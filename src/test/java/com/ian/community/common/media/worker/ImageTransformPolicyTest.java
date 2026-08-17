package com.ian.community.common.media.worker;

import com.ian.community.common.exception.ErrorCode;
import com.ian.community.common.media.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ImageTransformPolicyTest {
    private final ImageTransformEngine engine = new ImageTransformEngine();

    @Test
    void normalizedCropUsesHalfUpRoundingAndCentersFrameCorrection() {
        MediaAsset asset = asset(
                MediaFrame.POST_LANDSCAPE,
                "0.1", "0.1", "0.8", "0.8"
        );

        ImageTransformEngine.CropPixels crop = engine.cropPixels(asset, 1001, 1000);

        assertEquals(new ImageTransformEngine.CropPixels(100, 242, 801, 515), crop);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8})
    void allExifOrientationValuesUseAutoOrientBeforeUserRotation(int orientation) {
        List<String> command = engine.masterCommand(
                Path.of("orientation-" + orientation + ".jpg"),
                Path.of("master.webp")
        );
        assertTrue(command.contains("-auto-orient"));
        assertFalse(command.contains("-resize"));
        assertFalse(command.contains("-quality"));
    }

    @Test
    void exifAndUserRotationAreAppliedToTheCoordinateDimensions() {
        var rotatedByExif = engine.orientedDimensions(
                new ImageTransformEngine.ImageInfo("JPEG", 1200, 800, 1, "RightTop"),
                0
        );
        assertEquals(800, rotatedByExif.width());
        assertEquals(1200, rotatedByExif.height());

        var rotatedAgainByUser = engine.orientedDimensions(rotatedByExif, 90);
        assertEquals(1200, rotatedAgainByUser.width());
        assertEquals(800, rotatedAgainByUser.height());
    }

    @Test
    void userRotationIsLimitedToAnExplicitImagemagickRotationStep() {
        List<String> command = engine.rotationCommand(
                Path.of("master.webp"), Path.of("rotated.webp"), 270
        );
        assertEquals(List.of(
                "convert", "master.webp", "-rotate", "270", "+repage", "rotated.webp"
        ), command);
    }

    @Test
    void variantsNeverUpscaleBeyondTheEligibleSourceCrop() {
        assertEquals(
                List.of(MediaVariantType.POST_LANDSCAPE_1X),
                engine.eligibleVariants(MediaFrame.POST_LANDSCAPE, 900, 600)
        );
        assertEquals(
                List.of(MediaVariantType.POST_LANDSCAPE_1X),
                engine.eligibleVariants(MediaFrame.POST_LANDSCAPE, 320, 200)
        );
        assertEquals(
                List.of(MediaVariantType.POST_LANDSCAPE_1X, MediaVariantType.POST_LANDSCAPE_3X),
                engine.eligibleVariants(MediaFrame.POST_LANDSCAPE, 1600, 1000)
        );
        assertEquals(
                List.of(MediaVariantType.PROFILE_SMALL, MediaVariantType.PROFILE_MEDIUM),
                engine.eligibleVariants(MediaFrame.PROFILE, 200, 200)
        );
    }

    @Test
    void declaredMimeMustMatchMagicBytes() {
        byte[] jpeg = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x00};
        byte[] png = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        byte[] webp = {0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x45, 0x42, 0x50};
        byte[] bmp = {0x42, 0x4d, 0, 0};

        assertTrue(engine.matchesMagic(jpeg, "image/jpeg"));
        assertTrue(engine.matchesMagic(png, "image/png"));
        assertTrue(engine.matchesMagic(webp, "image/webp"));
        assertTrue(engine.matchesMagic(bmp, "image/bmp"));
        assertFalse(engine.matchesMagic(jpeg, "image/png"));
        assertFalse(engine.matchesMagic(webp, "image/gif"));
    }

    @Test
    void supportedFormatsArePreservedAndBmpFallsBackToJpeg() {
        assertEquals(MediaOutputFormat.JPEG, MediaOutputFormat.fromSourceFormat("JPEG"));
        assertEquals(MediaOutputFormat.PNG, MediaOutputFormat.fromSourceFormat("PNG"));
        assertEquals(MediaOutputFormat.WEBP, MediaOutputFormat.fromSourceFormat("WEBP"));
        assertEquals(MediaOutputFormat.JPEG, MediaOutputFormat.fromSourceFormat("BMP"));
        assertTrue(MediaOutputFormat.JPEG.encoderArguments().contains("4:4:4"));
        assertTrue(MediaOutputFormat.PNG.encoderArguments().contains("png:compression-level=6"));
        assertTrue(MediaOutputFormat.WEBP.encoderArguments().contains("webp:alpha-quality=100"));
    }

    @Test
    void animatedImagesAndOversizedDimensionsAreRejected() {
        PermanentMediaProcessingException animation = assertThrows(
                PermanentMediaProcessingException.class,
                () -> engine.validateInfo(new ImageTransformEngine.ImageInfo("WEBP", 800, 800, 2))
        );
        assertEquals(ErrorCode.ANIMATED_IMAGE_NOT_ALLOWED, animation.getErrorCode());

        PermanentMediaProcessingException dimensions = assertThrows(
                PermanentMediaProcessingException.class,
                () -> engine.validateInfo(new ImageTransformEngine.ImageInfo("JPEG", 12_001, 100, 1))
        );
        assertEquals(ErrorCode.IMAGE_DIMENSION_EXCEEDED, dimensions.getErrorCode());
    }

    @Test
    void expiredLeaseCanBeReclaimedButActiveLeaseAndReadyAssetCannot() {
        MediaAsset asset = asset(MediaFrame.PROFILE, "0", "0", "1", "1");
        asset.markUploaded();

        assertTrue(asset.claim(LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1)));
        assertTrue(asset.claim(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(5)));
        assertFalse(asset.claim(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(5)));

        asset.markReady("master", 320, 320);
        assertFalse(asset.claim(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(5)));
    }

    private MediaAsset asset(
            MediaFrame frame,
            String x,
            String y,
            String width,
            String height
    ) {
        return new MediaAsset(
                1L,
                frame == MediaFrame.PROFILE ? MediaPurpose.PROFILE : MediaPurpose.POST,
                frame,
                0,
                new BigDecimal(x),
                new BigDecimal(y),
                new BigDecimal(width),
                new BigDecimal(height),
                "image/jpeg",
                1024,
                1
        );
    }
}
