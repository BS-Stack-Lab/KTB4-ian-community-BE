package com.ian.community.common.media.worker;

import com.ian.community.common.exception.ErrorCode;

import java.util.List;

public enum MediaOutputFormat {
    JPEG("jpeg", "image/jpeg", List.of(
            "-sampling-factor", "4:4:4",
            "-quality", "95",
            "-define", "jpeg:dct-method=float"
    )),
    PNG("png", "image/png", List.of(
            "-define", "png:compression-level=6"
    )),
    WEBP("webp", "image/webp", List.of(
            "-quality", "90",
            "-define", "webp:alpha-quality=100"
    ));

    private final String extension;
    private final String mimeType;
    private final List<String> encoderArguments;

    MediaOutputFormat(String extension, String mimeType, List<String> encoderArguments) {
        this.extension = extension;
        this.mimeType = mimeType;
        this.encoderArguments = encoderArguments;
    }

    public String extension() {
        return extension;
    }

    public String mimeType() {
        return mimeType;
    }

    public List<String> encoderArguments() {
        return encoderArguments;
    }

    public static MediaOutputFormat fromSourceFormat(String format) {
        return switch (format) {
            case "JPEG" -> JPEG;
            case "PNG" -> PNG;
            case "WEBP" -> WEBP;
            case "BMP" -> JPEG;
            default -> throw new PermanentMediaProcessingException(ErrorCode.UNSUPPORTED_IMAGE_TYPE);
        };
    }
}
