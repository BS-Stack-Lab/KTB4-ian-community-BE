package com.ian.community.common.media.dto;

import com.ian.community.common.media.MediaFrame;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record MediaRevisionRequest(
        @NotNull MediaFrame frame,
        @NotNull @Valid CropRectRequest crop,
        @NotNull @DecimalMin("1.0") @DecimalMax("3.0") BigDecimal zoom,
        @NotNull @Valid MediaPositionRequest position
) {}
