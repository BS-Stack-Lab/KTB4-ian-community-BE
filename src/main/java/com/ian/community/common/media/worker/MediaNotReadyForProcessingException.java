package com.ian.community.common.media.worker;

final class MediaNotReadyForProcessingException extends RuntimeException {
    MediaNotReadyForProcessingException() {
        super("The S3 event arrived before upload completion was confirmed");
    }
}
