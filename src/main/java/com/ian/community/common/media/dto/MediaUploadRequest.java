package com.ian.community.common.media.dto;

import com.ian.community.common.media.MediaFrame;
import com.ian.community.common.media.MediaPurpose;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

public record MediaUploadRequest(
        @NotNull MediaPurpose purpose,
        @NotBlank String fileName,
        @NotBlank String contentType,
        @Min(1) long fileSize,
        @NotNull MediaFrame frame,
        int rotation,
        @NotNull @Valid CropRectRequest crop,
        @NotNull @DecimalMin("1.0") @DecimalMax("3.0") BigDecimal zoom,
        @NotNull @Valid MediaPositionRequest position
) {
    public MediaUploadRequest(
            MediaPurpose purpose,
            String fileName,
            String contentType,
            long fileSize,
            MediaFrame frame,
            int rotation,
            CropRectRequest crop
    ) {
        this(
                purpose, fileName, contentType, fileSize, frame, rotation, crop,
                BigDecimal.ONE,
                new MediaPositionRequest(
                        crop.x().add(crop.width().divide(BigDecimal.valueOf(2))),
                        crop.y().add(crop.height().divide(BigDecimal.valueOf(2)))
                )
        );
    }
}
