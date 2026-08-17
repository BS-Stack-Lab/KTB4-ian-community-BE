package com.ian.community.user.follow.dto;

import com.ian.community.common.SuccessCode;
import org.springframework.http.HttpStatus;

public enum ProfileSuccessCode implements SuccessCode {
    USER_PROFILE_FOUND("사용자 프로필을 조회했습니다.");

    private final String message;

    ProfileSuccessCode(String message) {
        this.message = message;
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.OK;
    }

    @Override
    public String getCode() {
        return name();
    }

    @Override
    public String getMessage() {
        return message;
    }
}
