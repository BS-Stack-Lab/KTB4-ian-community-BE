package com.ian.community.common.media;

public enum MediaFrame {
    PROFILE(1.0),
    POST_PORTRAIT(56.0 / 75.0),
    POST_LANDSCAPE(14.0 / 9.0);

    private final double ratio;

    MediaFrame(double ratio) {
        this.ratio = ratio;
    }

    public double getRatio() {
        return ratio;
    }
}
