package com.ian.community.common.media;

import java.util.Arrays;
import java.util.List;

public enum MediaVariantType {
    PROFILE_SMALL(34, 34, MediaFrame.PROFILE, "profile-34"),
    PROFILE_MEDIUM(160, 160, MediaFrame.PROFILE, "profile-160"),
    PROFILE_LARGE(320, 320, MediaFrame.PROFILE, "profile-320"),
    POST_PORTRAIT_1X(448, 600, MediaFrame.POST_PORTRAIT, "post-portrait-448"),
    POST_PORTRAIT_2X(896, 1200, MediaFrame.POST_PORTRAIT, "post-portrait-896"),
    POST_PORTRAIT_3X(1344, 1800, MediaFrame.POST_PORTRAIT, "post-portrait-1344"),
    POST_LANDSCAPE_1X(448, 288, MediaFrame.POST_LANDSCAPE, "post-landscape-448"),
    POST_LANDSCAPE_2X(896, 576, MediaFrame.POST_LANDSCAPE, "post-landscape-896"),
    POST_LANDSCAPE_3X(1344, 864, MediaFrame.POST_LANDSCAPE, "post-landscape-1344");

    private final int width;
    private final int height;
    private final MediaFrame frame;
    private final String keyName;

    MediaVariantType(int width, int height, MediaFrame frame, String keyName) {
        this.width = width;
        this.height = height;
        this.frame = frame;
        this.keyName = keyName;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public String getKeyName() {
        return keyName;
    }

    public static List<MediaVariantType> forFrame(MediaFrame frame) {
        return Arrays.stream(values())
                .filter(variant -> variant.frame == frame)
                .toList();
    }
}
