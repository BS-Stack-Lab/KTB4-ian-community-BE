package com.ian.community.common.media.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record MediaPositionRequest(
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal x,
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal y
) {}
