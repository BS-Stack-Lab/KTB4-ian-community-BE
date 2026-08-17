package com.ian.community.post.dto.response;

import com.ian.community.common.media.dto.MediaResponse;
import com.ian.community.post.domain.PostImageMediaState;
import com.ian.community.common.media.MediaFrame;

import java.util.UUID;

public record PostMediaAttachmentResponse(
        int order,
        PostImageMediaState state,
        MediaResponse activeMedia,
        UUID pendingMediaId,
        MediaFrame pendingFrame,
        String errorCode
) {}
