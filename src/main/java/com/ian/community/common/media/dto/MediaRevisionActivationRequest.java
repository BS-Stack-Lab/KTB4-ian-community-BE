package com.ian.community.common.media.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record MediaRevisionActivationRequest(
        @NotNull UUID mediaId,
        @Min(1) int revision
) {}
