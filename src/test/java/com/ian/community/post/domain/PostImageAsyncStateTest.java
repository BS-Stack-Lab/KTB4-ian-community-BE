package com.ian.community.post.domain;

import com.ian.community.common.media.MediaAsset;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class PostImageAsyncStateTest {
    @Test
    void pendingReplacementPreservesActiveUntilPromotionAndFailureKeepsIt() {
        Post post = mock(Post.class);
        MediaAsset active = mock(MediaAsset.class);
        MediaAsset pending = mock(MediaAsset.class);
        PostImage image = new PostImage(post, "/active.jpg", active, 0);

        image.replaceWithPending(pending, 0, UUID.randomUUID());
        assertEquals(PostImageMediaState.PROCESSING, image.getMediaState());
        assertSame(active, image.getMediaAsset());
        assertEquals("/active.jpg", image.getImageUrl());

        image.failPending("CORRUPTED_IMAGE");
        assertEquals(PostImageMediaState.FAILED, image.getMediaState());
        assertSame(active, image.getMediaAsset());
        assertNull(image.getPendingMedia());

        image.replaceWithPending(pending, 0, UUID.randomUUID());
        image.promotePending("/new.jpg");
        assertEquals(PostImageMediaState.READY, image.getMediaState());
        assertSame(pending, image.getMediaAsset());
        assertEquals("/new.jpg", image.getImageUrl());
    }
}
