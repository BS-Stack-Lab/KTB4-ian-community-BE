package com.ian.community.common.media.worker;

import com.ian.community.common.exception.ErrorCode;
import com.ian.community.common.media.MediaAsset;
import com.ian.community.common.media.MediaFrame;
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

        Path master = directory.resolve("master.webp");
        run(masterCommand(source, master));
        ImageInfo masterInfo = identify(master);

        Path edited = master;
        if (asset.getRotation() != 0) {
            edited = directory.resolve("rotated.webp");
            run(rotationCommand(master, edited, asset.getRotation()));
        }
        ImageInfo editedInfo = identify(edited);
        CropPixels crop = cropPixels(asset, editedInfo.width(), editedInfo.height());
        ensureMinimum(asset.getFrame(), crop.width(), crop.height());

        Path cropped = directory.resolve("crop.webp");
        run(List.of(
                "convert", edited.toString(),
                "-crop", crop.width() + "x" + crop.height() + "+" + crop.x() + "+" + crop.y(),
                "+repage", "-strip", "-colorspace", "sRGB",
                "-quality", "95", cropped.toString()
        ));

        List<TransformedVariant> outputs = new ArrayList<>();
        for (MediaVariantType type : eligibleVariants(asset.getFrame(), crop.width(), crop.height())) {
            Path output = directory.resolve(type.getKeyName() + ".webp");
            run(List.of(
                    "convert", cropped.toString(),
                    "-resize", type.getWidth() + "x" + type.getHeight() + "!",
                    "-strip", "-colorspace", "sRGB",
                    "-quality", "82", output.toString()
            ));
            try {
                outputs.add(new TransformedVariant(type, output, Files.size(output)));
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to inspect transformed image", exception);
            }
        }
        if (outputs.isEmpty()) {
            throw new PermanentMediaProcessingException(ErrorCode.IMAGE_TOO_SMALL);
        }
        return new TransformedMedia(master, masterInfo.width(), masterInfo.height(), List.copyOf(outputs));
    }

    public TransformedMedia transformRevision(
            MediaRevision revision,
            Path master,
            Path directory
    ) {
        ImageInfo masterInfo = identify(master);
        validateInfo(masterInfo);
        Path edited = master;
        if (revision.getRotation() != 0) {
            edited = directory.resolve("rotated.webp");
            run(rotationCommand(master, edited, revision.getRotation()));
        }
        ImageInfo editedInfo = identify(edited);
        CropPixels crop = cropPixels(revision, editedInfo.width(), editedInfo.height());
        ensureMinimum(revision.getFrame(), crop.width(), crop.height());

        Path cropped = directory.resolve("crop.webp");
        run(List.of(
                "convert", edited.toString(),
                "-crop", crop.width() + "x" + crop.height() + "+" + crop.x() + "+" + crop.y(),
                "+repage", "-strip", "-colorspace", "sRGB",
                "-quality", "95", cropped.toString()
        ));

        List<TransformedVariant> outputs = new ArrayList<>();
        for (MediaVariantType type : eligibleVariants(
                revision.getFrame(), crop.width(), crop.height()
        )) {
            Path output = directory.resolve(type.getKeyName() + ".webp");
            run(List.of(
                    "convert", cropped.toString(),
                    "-resize", type.getWidth() + "x" + type.getHeight() + "!",
                    "-strip", "-colorspace", "sRGB",
                    "-quality", "82", output.toString()
            ));
            try {
                outputs.add(new TransformedVariant(type, output, Files.size(output)));
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to inspect transformed image", exception);
            }
        }
        if (outputs.isEmpty()) {
            throw new PermanentMediaProcessingException(ErrorCode.IMAGE_TOO_SMALL);
        }
        return new TransformedMedia(
                master, masterInfo.width(), masterInfo.height(), List.copyOf(outputs)
        );
    }

    void validateInfo(ImageInfo info) {
        if (!List.of("JPEG", "PNG", "WEBP").contains(info.format())) {
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
            default -> false;
        };
    }

    private boolean startsWith(byte[] value, byte[] prefix) {
        return value.length >= prefix.length
                && Arrays.equals(Arrays.copyOf(value, prefix.length), prefix);
    }

    private ImageInfo identify(Path path) {
        String output = run(List.of(
                "identify", "-ping", "-format", "%m|%w|%h|%n\\n", path.toString()
        )).trim();
        String[] lines = output.split("\\R");
        if (lines.length != 1) {
            throw new PermanentMediaProcessingException(ErrorCode.ANIMATED_IMAGE_NOT_ALLOWED);
        }
        String[] values = lines[0].split("\\|");
        if (values.length != 4) {
            throw new PermanentMediaProcessingException(ErrorCode.CORRUPTED_IMAGE);
        }
        try {
            return new ImageInfo(
                    values[0].toUpperCase(),
                    Integer.parseInt(values[1]),
                    Integer.parseInt(values[2]),
                    Integer.parseInt(values[3])
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
        return MediaVariantType.forFrame(frame).stream()
                .filter(type -> width >= type.getWidth() && height >= type.getHeight())
                .toList();
    }

    List<String> masterCommand(Path source, Path master) {
        return List.of(
                "convert", source.toString(),
                "-auto-orient", "-strip", "-colorspace", "sRGB",
                "-resize", "4096x4096>",
                "-quality", "95",
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
        MediaVariantType minimum = MediaVariantType.forFrame(frame).getFirst();
        if (width < minimum.getWidth() || height < minimum.getHeight()) {
            throw new PermanentMediaProcessingException(ErrorCode.IMAGE_TOO_SMALL);
        }
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

    record ImageInfo(String format, int width, int height, int frames) {}
    record CropPixels(int x, int y, int width, int height) {}
}
