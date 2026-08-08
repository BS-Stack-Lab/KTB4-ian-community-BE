package com.ian.community.common.media.worker;

import com.ian.community.common.exception.ErrorCode;

public class PermanentMediaProcessingException extends RuntimeException {
    private final ErrorCode errorCode;

    public PermanentMediaProcessingException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
