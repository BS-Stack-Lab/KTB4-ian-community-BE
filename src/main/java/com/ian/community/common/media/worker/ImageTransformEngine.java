package com.ian.community.common.media.worker;

import com.ian.community.common.exception.ErrorCode;
import com.ian.community.common.media.MediaAsset;
import com.ian.community.common.media.MediaFrame;
import com.ian.community.common.media.MediaQualityLevel;
import com.ian.community.common.media.MediaRevision;
import com.ian.community.common.media.MediaVariantType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "app.runtime", havingValue = "worker")
public class ImageTransformEngine {
    private static final int MAX_DIMENSION = 12_000;
    private static final long MAX_PIXELS = 50_000_000L;
    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(120);

    public TransformedMedia transform(MediaAsset asset, Path source, Path directory) {
        validateMagic(source, asset.getDeclaredContentType());
        ImageInfo sourceInfo = identify(source);
        validateInfo(sourceInfo);
        MediaOutputFormat outputFormat = MediaOutputFormat.fromSourceFormat(sourceInfo.format());
        return transformOriginal(
                source,
                directory,
                asset.getFrame(),
                asset.getRotation(),
                cropPixels(asset, orientedDimensions(sourceInfo, asset.getRotation())),
                sourceInfo,
                outputFormat
        );
    }

    public TransformedMedia transformRevision(
            MediaRevision revision,
            Path master,
            Path directory
    ) {
        ImageInfo sourceInfo = identify(master);
        validateInfo(sourceInfo);
        MediaOutputFormat outputFormat = MediaOutputFormat.fromSourceFormat(sourceInfo.format());
        ImageInfo dimensions = orientedDimensions(sourceInfo, revision.getRotation());
        return transformOriginal(
                master,
                directory,
                revision.getFrame(),
                revision.getRotation(),
                cropPixels(revision, dimensions.width(), dimensions.height()),
                sourceInfo,
                outputFormat
        );
    }

    private TransformedMedia transformOriginal(
            Path source,
            Path directory,
            MediaFrame frame,
            int rotation,
            CropPixels crop,
            ImageInfo sourceInfo,
            MediaOutputFormat outputFormat
    ) {
        ensureMinimum(frame, crop.width(), crop.height());
        List<TransformedVariant> outputs = new ArrayList<>();
        for (MediaVariantType type : eligibleVariants(frame, crop.width(), crop.height())) {
            Path output = directory.resolve(type.getKeyName() + "." + outputFormat.extension());
            List<String> command = new ArrayList<>(List.of(
                    "convert", source.toString(), "-auto-orient"
            ));
            if (rotation != 0) {
                command.addAll(List.of("-rotate", Integer.toString(rotation), "+repage"));
            }
            command.addAll(List.of(
                    "-crop", crop.width() + "x" + crop.height() + "+" + crop.x() + "+" + crop.y(),
                    "+repage",
                    "-resize", type.getWidth() + "x" + type.getHeight() + "!",
                    "-strip", "-colorspace", "sRGB"
            ));
            command.addAll(outputFormat.encoderArguments());
            command.add(output.toString());
            run(command);
            ImageInfo outputInfo = identify(output);
            if (!outputInfo.format().equals(outputFormat.name())
                    || outputInfo.width() != type.getWidth()
                    || outputInfo.height() != type.getHeight()
                    || outputInfo.frames() != 1) {
                throw new PermanentMediaProcessingException(ErrorCode.CORRUPTED_IMAGE);
            }
            try {
                outputs.add(new TransformedVariant(
                        type, output, Files.size(output), outputFormat
                ));
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to inspect transformed image", exception);
            }
        }
        if (outputs.isEmpty()) {
            throw new PermanentMediaProcessingException(ErrorCode.IMAGE_TOO_SMALL);
        }
        ImageInfo dimensions = orientedDimensions(sourceInfo, 0);
        Quality quality = quality(frame, crop);
        return new TransformedMedia(
                source,
                dimensions.width(),
                dimensions.height(),
                sourceInfo.format(),
                outputFormat,
                crop.width(),
                crop.height(),
                quality.level(),
                quality.upscaleRatio1x(),
                List.copyOf(outputs)
        );
    }

    ImageInfo orientedDimensions(ImageInfo source, int rotation) {
        boolean exifSwapsAxes = List.of(
                "LEFTTOP", "RIGHTTOP", "RIGHTBOTTOM", "LEFTBOTTOM"
        ).contains(source.orientation().replaceAll("[^A-Za-z]", "").toUpperCase());
        int width = exifSwapsAxes ? source.height() : source.width();
        int height = exifSwapsAxes ? source.width() : source.height();
        if (rotation == 90 || rotation == 270) {
            int swap = width;
            width = height;
            height = swap;
        }
        return new ImageInfo(source.format(), width, height, source.frames(), "TOPLEFT");
    }

    private CropPixels cropPixels(MediaAsset asset, ImageInfo dimensions) {
        return cropPixels(asset, dimensions.width(), dimensions.height());
    }

    void validateInfo(ImageInfo info) {
        if (!List.of("JPEG", "PNG", "WEBP", "BMP").contains(info.format())) {
            throw new PermanentMediaProcessingException(ErrorCode.UNSUPPORTED_IMAGE_TYPE);
        }
        if (info.frames() != 1) {
            throw new PermanentMediaProcessingException(ErrorCode.ANIMATED_IMAGE_NOT_ALLOWED);
        }
        if (info.width() > MAX_DIMENSION || info.height() > MAX_DIMENSION
                || (long) info.width() * info.height() > MAX_PIXELS) {
            throw new PermanentMediaProcessingException(ErrorCode.IMAGE_DIMENSION_EXCEEDED);
        }
    }

    private void validateMagic(Path source, String declaredType) {
        try {
            byte[] bytes = Files.readAllBytes(source);
            if (!matchesMagic(bytes, declaredType)) {
                throw new PermanentMediaProcessingException(ErrorCode.UNSUPPORTED_IMAGE_TYPE);
            }
        } catch (IOException exception) {
            throw new PermanentMediaProcessingException(ErrorCode.CORRUPTED_IMAGE);
        }
    }

    boolean matchesMagic(byte[] bytes, String declaredType) {
        return switch (declaredType) {
            case "image/jpeg" -> startsWith(bytes, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
            case "image/png" -> startsWith(bytes, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
            case "image/webp" -> bytes.length >= 12
                    && startsWith(bytes, new byte[]{0x52, 0x49, 0x46, 0x46})
                    && Arrays.equals(Arrays.copyOfRange(bytes, 8, 12), new byte[]{0x57, 0x45, 0x42, 0x50});
            case "image/bmp" -> bytes.length >= 2 && bytes[0] == 0x42 && bytes[1] == 0x4d;
            default -> false;
        };
    }

    private boolean startsWith(byte[] value, byte[] prefix) {
        return value.length >= prefix.length
                && Arrays.equals(Arrays.copyOf(value, prefix.length), prefix);
    }

    private ImageInfo identify(Path path) {
        String output = run(List.of(
                "identify", "-ping", "-format", "%m|%w|%h|%n|%[orientation]\\n", path.toString()
        )).trim();
        String[] lines = output.split("\\R");
        if (lines.length != 1) {
            throw new PermanentMediaProcessingException(ErrorCode.ANIMATED_IMAGE_NOT_ALLOWED);
        }
        String[] values = lines[0].split("\\|", -1);
        if (values.length != 5) {
            throw new PermanentMediaProcessingException(ErrorCode.CORRUPTED_IMAGE);
        }
        try {
            return new ImageInfo(
                    values[0].toUpperCase(),
                    Integer.parseInt(values[1]),
                    Integer.parseInt(values[2]),
                    Integer.parseInt(values[3]),
                    values[4].isBlank() ? "TOPLEFT" : values[4]
            );
        } catch (NumberFormatException exception) {
            throw new PermanentMediaProcessingException(ErrorCode.CORRUPTED_IMAGE);
        }
    }

    CropPixels cropPixels(MediaAsset asset, int width, int height) {
        return cropPixels(
                asset.getFrame(), asset.getCropX(), asset.getCropY(),
                asset.getCropWidth(), asset.getCropHeight(), width, height
        );
    }

    CropPixels cropPixels(MediaRevision revision, int width, int height) {
        return cropPixels(
                revision.getFrame(), revision.getCropX(), revision.getCropY(),
                revision.getCropWidth(), revision.getCropHeight(), width, height
        );
    }

    private CropPixels cropPixels(
            MediaFrame frame,
            BigDecimal normalizedX,
            BigDecimal normalizedY,
            BigDecimal normalizedWidth,
            BigDecimal normalizedHeight,
            int width,
            int height
    ) {
        int x = pixels(normalizedX, width);
        int y = pixels(normalizedY, height);
        int cropWidth = Math.max(1, pixels(normalizedWidth, width));
        int cropHeight = Math.max(1, pixels(normalizedHeight, height));
        cropWidth = Math.min(cropWidth, width - x);
        cropHeight = Math.min(cropHeight, height - y);

        double targetRatio = frame.getRatio();
        if ((double) cropWidth / cropHeight > targetRatio) {
            int corrected = Math.max(1, (int) Math.round(cropHeight * targetRatio));
            x += (cropWidth - corrected) / 2;
            cropWidth = corrected;
        } else {
            int corrected = Math.max(1, (int) Math.round(cropWidth / targetRatio));
            y += (cropHeight - corrected) / 2;
            cropHeight = corrected;
        }
        if (x < 0 || y < 0 || x + cropWidth > width || y + cropHeight > height) {
            throw new PermanentMediaProcessingException(ErrorCode.INVALID_CROP_RECT);
        }
        return new CropPixels(x, y, cropWidth, cropHeight);
    }

    List<MediaVariantType> eligibleVariants(MediaFrame frame, int width, int height) {
        List<MediaVariantType> variants = MediaVariantType.forFrame(frame);
        if (frame == MediaFrame.PROFILE) {
            return variants.stream()
                    .filter(type -> width >= type.getWidth() && height >= type.getHeight())
                    .toList();
        }
        MediaVariantType oneX = variants.getFirst();
        MediaVariantType threeX = variants.getLast();
        if (width >= threeX.getWidth() && height >= threeX.getHeight()) {
            return List.of(oneX, threeX);
        }
        // A low-resolution crop is deliberately allowed and upscaled to 1X. The
        // quality level is persisted and the editor warns without blocking publish.
        return List.of(oneX);
    }

    List<String> masterCommand(Path source, Path master) {
        return List.of(
                "convert", source.toString(),
                "-auto-orient",
                master.toString()
        );
    }

    List<String> rotationCommand(Path source, Path output, int rotation) {
        return List.of(
                "convert", source.toString(),
                "-rotate", Integer.toString(rotation),
                "+repage", output.toString()
        );
    }

    private int pixels(BigDecimal normalized, int dimension) {
        return normalized.multiply(BigDecimal.valueOf(dimension))
                .setScale(0, RoundingMode.HALF_UP)
                .intValueExact();
    }

    private void ensureMinimum(MediaFrame frame, int width, int height) {
        if (frame != MediaFrame.PROFILE) {
            return;
        }
        MediaVariantType minimum = MediaVariantType.forFrame(frame).getFirst();
        if (width < minimum.getWidth() || height < minimum.getHeight()) {
            throw new PermanentMediaProcessingException(ErrorCode.IMAGE_TOO_SMALL);
        }
    }

    private Quality quality(MediaFrame frame, CropPixels crop) {
        if (frame == MediaFrame.PROFILE) {
            return new Quality(MediaQualityLevel.GOOD, BigDecimal.ONE);
        }
        List<MediaVariantType> variants = MediaVariantType.forFrame(frame);
        MediaVariantType oneX = variants.getFirst();
        MediaVariantType threeX = variants.getLast();
        BigDecimal ratio1x = BigDecimal.valueOf(Math.min(
                (double) crop.width() / oneX.getWidth(),
                (double) crop.height() / oneX.getHeight()
        )).setScale(4, RoundingMode.HALF_UP);
        boolean supportsThreeX = crop.width() >= threeX.getWidth()
                && crop.height() >= threeX.getHeight();
        MediaQualityLevel level = ratio1x.compareTo(BigDecimal.ONE) < 0
                ? MediaQualityLevel.LOW
                : supportsThreeX
                ? MediaQualityLevel.GOOD
                : MediaQualityLevel.STANDARD_ONLY;
        BigDecimal upscale = ratio1x.compareTo(BigDecimal.ONE) < 0
                ? BigDecimal.ONE.divide(ratio1x, 4, RoundingMode.HALF_UP)
                : BigDecimal.ONE;
        return new Quality(level, upscale);
    }

    private String run(List<String> command) {
        Process process = null;
        try {
            process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            boolean completed = process.waitFor(COMMAND_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                throw new PermanentMediaProcessingException(ErrorCode.PROCESSING_TIMEOUT);
            }
            String output = new String(process.getInputStream().readAllBytes());
            if (process.exitValue() != 0) {
                throw new PermanentMediaProcessingException(ErrorCode.CORRUPTED_IMAGE);
            }
            return output;
        } catch (IOException exception) {
            throw new IllegalStateException("ImageMagick is unavailable", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Image processing interrupted", exception);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    record ImageInfo(String format, int width, int height, int frames, String orientation) {
        ImageInfo(String format, int width, int height, int frames) {
            this(format, width, height, frames, "TOPLEFT");
        }
    }
    record CropPixels(int x, int y, int width, int height) {}
    record Quality(MediaQualityLevel level, BigDecimal upscaleRatio1x) {}
}
